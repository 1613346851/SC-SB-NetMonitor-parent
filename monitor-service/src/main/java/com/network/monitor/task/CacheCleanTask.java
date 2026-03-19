package com.network.monitor.task;

import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存清理定时任务
 */
@Slf4j
@Component
public class CacheCleanTask {

    @Autowired
    private LocalCacheService localCacheService;

    /**
     * 每 10 分钟清理一次过期缓存
     */
    @Scheduled(cron = "${cache.clean.cron:0 */10 * * * ?}")
    public void cleanExpiredCache() {
        try {
            localCacheService.cleanExpired();
            log.info("定时清理过期缓存完成，当前缓存大小：{}", localCacheService.size());
        } catch (Exception e) {
            log.error("定时清理缓存失败：", e);
        }
    }

    /**
     * 每天凌晨 2 点清空所有临时缓存（可选）
     */
    @Scheduled(cron = "${cache.deep.clean.cron:0 0 2 * * ?}")
    public void deepClean() {
        try {
            // 保留规则缓存和漏洞缓存，清除其他临时缓存
            localCacheService.clearByPrefix("cache:stat:");
            localCacheService.clearByPrefix("cache:traffic:");
            localCacheService.clearByPrefix("cache:attack:");
            
            log.info("深度清理临时缓存完成");
        } catch (Exception e) {
            log.error("深度清理缓存失败：", e);
        }
    }
}
