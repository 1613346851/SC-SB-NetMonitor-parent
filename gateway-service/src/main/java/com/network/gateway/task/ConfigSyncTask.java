package com.network.gateway.task;

import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 配置同步定时任务
 * 定期从监测服务同步配置，确保配置一致性
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
@EnableScheduling
public class ConfigSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSyncTask.class);

    @Autowired
    private GatewayConfigCache configCache;

    private final AtomicLong lastSyncTime = new AtomicLong(0);
    private final AtomicLong syncSuccessCount = new AtomicLong(0);
    private final AtomicLong syncFailureCount = new AtomicLong(0);

    @Value("${gateway.config.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${gateway.config.sync.interval:300000}")
    private long syncInterval;

    @Scheduled(fixedDelayString = "${gateway.config.sync.interval:300000}", initialDelay = 60000)
    public void syncConfigTask() {
        if (!syncEnabled) {
            logger.debug("配置同步任务已禁用");
            return;
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
            
        } catch (Exception e) {
            syncFailureCount.incrementAndGet();
            logger.error("配置同步任务执行异常: {}", e.getMessage(), e);
        }
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
