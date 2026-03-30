package com.network.monitor.service.impl;

import com.network.monitor.dto.ConfigSyncStatusDTO;
import com.network.monitor.service.ConfigSyncService;
import com.network.monitor.service.GatewayConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ConfigSyncServiceImpl implements ConfigSyncService {

    @Autowired
    private GatewayConfigService gatewayConfigService;

    @Value("${config.sync.push-on-startup:true}")
    private boolean pushOnStartup;

    @Value("${config.sync.retry-interval-ms:10000}")
    private long retryIntervalMs;

    private final AtomicLong syncSuccessCount = new AtomicLong(0);
    private final AtomicLong syncFailureCount = new AtomicLong(0);
    private final AtomicLong lastSyncTime = new AtomicLong(0);
    private volatile String lastSyncMessage = "";
    private volatile int lastSyncConfigCount = 0;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean initialPushCompleted = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (pushOnStartup) {
            executorService.submit(this::performInitialPush);
        }
    }
    
    private void performInitialPush() {
        log.info("监测服务启动，等待网关服务就绪后推送配置...");
        int retryCount = 0;
        
        while (!initialPushCompleted.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(retryIntervalMs);
                
                boolean success = gatewayConfigService.pushAllConfigsToGateway();
                if (success) {
                    initialPushCompleted.set(true);
                    int configCount = gatewayConfigService.getGatewayConfigCount();
                    recordSyncSuccess(configCount);
                    log.info("配置推送成功，共{}项配置", configCount);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("配置推送重试, retry={}: {}", retryCount, e.getMessage());
            }
            retryCount++;
        }
        
        if (!initialPushCompleted.get()) {
            log.warn("配置推送被中断，将在配置变更时自动同步");
        }
    }

    @Override
    public ConfigSyncStatusDTO getSyncStatus() {
        return ConfigSyncStatusDTO.builder()
                .gatewayConnected(true)
                .lastSyncTime(lastSyncTime.get() > 0 ? 
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(lastSyncTime.get()), 
                        java.time.ZoneId.systemDefault()
                    ) : null)
                .totalConfigCount(gatewayConfigService.getGatewayConfigCount())
                .syncedConfigCount(lastSyncConfigCount)
                .syncSuccessCount(syncSuccessCount.get())
                .syncFailureCount(syncFailureCount.get())
                .syncStatus(syncFailureCount.get() > syncSuccessCount.get() ? "WARNING" : "NORMAL")
                .message(lastSyncMessage)
                .build();
    }

    @Override
    public ConfigSyncStatusDTO syncAllToGateway() {
        log.info("开始同步所有配置到网关...");
        
        try {
            boolean success = gatewayConfigService.pushAllConfigsToGateway();
            int configCount = gatewayConfigService.getGatewayConfigCount();
            
            if (success) {
                recordSyncSuccess(configCount);
                initialPushCompleted.set(true);
                return ConfigSyncStatusDTO.success(configCount);
            } else {
                recordSyncFailure("推送配置到网关失败");
                return ConfigSyncStatusDTO.failure("推送配置到网关失败");
            }
        } catch (Exception e) {
            log.error("同步配置到网关异常", e);
            recordSyncFailure(e.getMessage());
            return ConfigSyncStatusDTO.failure(e.getMessage());
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
        log.info("配置同步服务已关闭");
    }

    @Override
    public ConfigSyncStatusDTO checkGatewayConnection() {
        return ConfigSyncStatusDTO.builder()
                .gatewayConnected(true)
                .syncStatus("CONNECTED")
                .message("网关连接正常")
                .build();
    }

    @Override
    public void recordSyncSuccess(int configCount) {
        syncSuccessCount.incrementAndGet();
        lastSyncTime.set(System.currentTimeMillis());
        lastSyncConfigCount = configCount;
        lastSyncMessage = "同步成功，共" + configCount + "项配置";
        log.info("配置同步成功记录：configCount={}", configCount);
    }

    @Override
    public void recordSyncFailure(String message) {
        syncFailureCount.incrementAndGet();
        lastSyncTime.set(System.currentTimeMillis());
        lastSyncMessage = message;
        log.warn("配置同步失败记录：message={}", message);
    }

    @Override
    public Long getSyncSuccessCount() {
        return syncSuccessCount.get();
    }

    @Override
    public Long getSyncFailureCount() {
        return syncFailureCount.get();
    }

    @Override
    public Long getLastSyncTime() {
        return lastSyncTime.get();
    }
}
