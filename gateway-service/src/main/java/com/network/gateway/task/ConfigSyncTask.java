package com.network.gateway.task;

import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ConfigSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSyncTask.class);

    @Autowired
    private GatewayConfigCache configCache;

    private final AtomicLong lastSyncTime = new AtomicLong(0);
    private final AtomicLong syncSuccessCount = new AtomicLong(0);
    private final AtomicLong syncFailureCount = new AtomicLong(0);

    private volatile boolean syncEnabled = true;
    private long syncInterval = 300000;

    public boolean syncConfig() {
        if (!syncEnabled) {
            logger.debug("配置同步任务已禁用");
            return false;
        }

        try {
            logger.info("开始执行配置同步任务...");
            
            boolean success = configCache.pullFromMonitorService();
            lastSyncTime.set(System.currentTimeMillis());
            
            if (success) {
                syncSuccessCount.incrementAndGet();
                logger.info("配置同步任务执行成功，当前配置数量: {}", configCache.size());
            } else {
                syncFailureCount.incrementAndGet();
                logger.warn("配置同步任务执行失败，使用本地缓存配置");
            }
            
            return success;
            
        } catch (Exception e) {
            syncFailureCount.incrementAndGet();
            logger.error("配置同步任务执行异常: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean shouldSync() {
        if (!syncEnabled) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        long lastSync = lastSyncTime.get();
        
        return (now - lastSync) >= syncInterval;
    }

    public void onConfigChangeNotification(String newVersion) {
        logger.info("收到配置变更通知，触发同步: newVersion={}", newVersion);
        syncConfig();
    }

    public long getLastSyncTime() {
        return lastSyncTime.get();
    }

    public long getSyncSuccessCount() {
        return syncSuccessCount.get();
    }

    public long getSyncFailureCount() {
        return syncFailureCount.get();
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean enabled) {
        this.syncEnabled = enabled;
        logger.info("配置同步任务状态已更新: {}", enabled ? "启用" : "禁用");
    }

    public void setSyncInterval(long interval) {
        this.syncInterval = interval;
    }

    public String getSyncStatistics() {
        return String.format(
            "配置同步统计 - 启用状态:%s, 成功次数:%d, 失败次数:%d, 上次同步时间:%d, 同步间隔:%dms",
            syncEnabled ? "启用" : "禁用",
            syncSuccessCount.get(),
            syncFailureCount.get(),
            lastSyncTime.get(),
            syncInterval
        );
    }
}
