package com.network.gateway.service;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.client.MonitorServiceConfigClient;
import com.network.gateway.dto.ConfigSyncRequest;
import com.network.gateway.dto.ConfigSyncResponse;
import com.network.gateway.entity.ConfigSyncState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ConfigSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSyncService.class);

    @Value("${spring.application.name:gateway-service}")
    private String applicationName;

    @Value("${config.sync.interval-ms:300000}")
    private long syncIntervalMs;

    @Value("${config.sync.retry-count:3}")
    private int maxRetryCount;

    @Value("${config.sync.timeout-ms:30000}")
    private long syncTimeoutMs;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private MonitorServiceConfigClient configClient;

    private final Map<String, ConfigSyncState> syncStateMap = new ConcurrentHashMap<>();
    private String gatewayId;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean initialSyncCompleted = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        this.gatewayId = generateGatewayId();
        logger.info("配置同步服务初始化, gatewayId={}", gatewayId);
        
        ConfigSyncState state = new ConfigSyncState(gatewayId);
        syncStateMap.put(gatewayId, state);
        
        executorService.submit(this::performInitialSync);
    }

    private String generateGatewayId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            return applicationName + "_" + hostname + "_" + uuid;
        } catch (Exception e) {
            return applicationName + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private void performInitialSync() {
        logger.info("网关服务启动，等待监测服务就绪后同步配置...");
        long retryInterval = 10000;
        int retryCount = 0;
        
        while (!initialSyncCompleted.get() && !Thread.currentThread().isInterrupted()) {
            try {
                boolean success = performTcpLikeHandshake();
                if (success) {
                    initialSyncCompleted.set(true);
                    logger.info("配置同步成功");
                    return;
                }
            } catch (Exception e) {
                logger.debug("配置同步重试, retry={}: {}", retryCount, e.getMessage());
            }
            retryCount++;
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!initialSyncCompleted.get()) {
            logger.warn("配置同步被中断，使用本地默认配置");
        }
    }

    public boolean performTcpLikeHandshake() {
        ConfigSyncState state = syncStateMap.get(gatewayId);
        if (state == null) {
            state = new ConfigSyncState(gatewayId);
            syncStateMap.put(gatewayId, state);
        }

        if (syncInProgress.get()) {
            logger.debug("配置同步正在进行中，跳过");
            return false;
        }

        syncInProgress.set(true);
        try {
            return doHandshake(state);
        } finally {
            syncInProgress.set(false);
        }
    }

    private boolean doHandshake(ConfigSyncState state) {
        String requestId = generateRequestId();
        
        logger.debug("开始TCP-like三次握手配置同步: requestId={}", requestId);

        ConfigSyncRequest step1Request = ConfigSyncRequest.createSyncRequest(gatewayId, state.getCurrentVersion());
        step1Request.setRequestId(requestId);
        
        state.markSyncing(null, requestId);
        state.advanceHandshake();
        logger.debug("握手步骤1: 发送同步请求, requestId={}, currentVersion={}", requestId, state.getCurrentVersion());

        ConfigSyncResponse step1Response = sendSyncRequest(step1Request);
        if (step1Response == null || !step1Response.isSuccess()) {
            state.markFailed();
            logger.debug("握手步骤1失败: {}", step1Response != null ? step1Response.getMessage() : "无响应");
            return false;
        }

        if (step1Response.getLatestVersion() != null && 
            step1Response.getLatestVersion().equals(state.getCurrentVersion())) {
            state.markSynced(state.getCurrentVersion());
            logger.info("配置已是最新版本: {}", state.getCurrentVersion());
            return true;
        }

        state.advanceHandshake();
        logger.debug("握手步骤2: 收到服务端响应, latestVersion={}, 准备确认", step1Response.getLatestVersion());

        ConfigSyncRequest step2Request = new ConfigSyncRequest();
        step2Request.setGatewayId(gatewayId);
        step2Request.setCurrentVersion(state.getCurrentVersion());
        step2Request.setRequestTimestamp(System.currentTimeMillis());
        step2Request.setSyncType("ACK");
        step2Request.setRequestId(requestId);

        ConfigSyncResponse step2Response = sendSyncRequest(step2Request);
        if (step2Response == null || !step2Response.isSuccess()) {
            state.markFailed();
            logger.debug("握手步骤2失败: {}", step2Response != null ? step2Response.getMessage() : "无响应");
            return false;
        }

        state.advanceHandshake();
        logger.debug("握手步骤3: 发送ACK确认, 开始应用配置");

        if (step2Response.getConfigs() != null && !step2Response.getConfigs().isEmpty()) {
            configCache.updateConfigs(step2Response.getConfigs());
            logger.info("应用新配置成功, 共{}项", step2Response.getConfigs().size());
        }

        state.markSynced(step2Response.getLatestVersion());
        logger.info("配置同步完成: version={}", state.getCurrentVersion());

        return true;
    }

    private ConfigSyncResponse sendSyncRequest(ConfigSyncRequest request) {
        try {
            Map<String, String> configs = configClient.pullAllConfigs();
            if (configs != null && !configs.isEmpty()) {
                String version = calculateVersion(configs);
                return ConfigSyncResponse.success(version, configs);
            }
            return ConfigSyncResponse.failure("无法获取配置");
        } catch (Exception e) {
            logger.debug("发送同步请求失败: {}", e.getMessage());
            return ConfigSyncResponse.failure(e.getMessage());
        }
    }

    private String calculateVersion(Map<String, String> configs) {
        int hash = configs.hashCode();
        long timestamp = System.currentTimeMillis();
        return "v" + timestamp + "_" + Integer.toHexString(hash).substring(0, 8);
    }

    private String generateRequestId() {
        return gatewayId + "_" + System.currentTimeMillis() + "_" + 
            UUID.randomUUID().toString().substring(0, 8);
    }

    public void checkAndSyncIfNeeded() {
        ConfigSyncState state = syncStateMap.get(gatewayId);
        if (state == null) {
            return;
        }

        if (state.needsSync(syncIntervalMs) && !state.isSyncing()) {
            logger.info("配置同步间隔已到，开始同步");
            executorService.submit(this::performTcpLikeHandshake);
        }
    }

    public boolean onConfigChangeNotification(String newVersion) {
        logger.info("收到配置变更通知: newVersion={}", newVersion);
        
        ConfigSyncState state = syncStateMap.get(gatewayId);
        if (state != null && newVersion != null && !newVersion.equals(state.getCurrentVersion())) {
            executorService.submit(this::performTcpLikeHandshake);
            return true;
        }
        return false;
    }

    public ConfigSyncState getSyncState() {
        return syncStateMap.get(gatewayId);
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getCurrentVersion() {
        ConfigSyncState state = syncStateMap.get(gatewayId);
        return state != null ? state.getCurrentVersion() : null;
    }

    public String getSyncStatus() {
        ConfigSyncState state = syncStateMap.get(gatewayId);
        return state != null ? state.getSyncStatus() : ConfigSyncState.STATUS_PENDING;
    }

    public void forceSync() {
        logger.info("强制触发配置同步");
        executorService.submit(this::performTcpLikeHandshake);
    }

    public String getStats() {
        ConfigSyncState state = syncStateMap.get(gatewayId);
        if (state == null) {
            return "无同步状态";
        }
        return String.format("配置同步状态 - gatewayId=%s, version=%s, status=%s, failCount=%d, lastSync=%d",
            state.getGatewayId(), 
            state.getCurrentVersion(), 
            state.getSyncStatus(), 
            state.getSyncFailCount(),
            state.getLastSyncTime());
    }

    public void shutdown() {
        executorService.shutdown();
        logger.info("配置同步服务已关闭");
    }
}
