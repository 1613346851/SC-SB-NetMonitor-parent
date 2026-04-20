package com.network.monitor.task;

import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheCleanTask {

    @Autowired
    private LocalCacheService localCacheService;

    @Scheduled(cron = "${cache.clean.cron:0 */10 * * * ?}")
    public void cleanExpiredCache() {
        try {
            int beforeSize = localCacheService.size();
            localCacheService.cleanExpired();
            int afterSize = localCacheService.size();
            
            if (beforeSize != afterSize) {
                log.info("定时清理过期缓存完成，清理数量：{}，当前缓存大小：{}", beforeSize - afterSize, afterSize);
            }
        } catch (Exception e) {
            log.error("定时清理缓存失败：", e);
        }
    }

    @Scheduled(cron = "${cache.deep.clean.cron:0 0 2 * * ?}")
    public void deepClean() {
        try {
            localCacheService.clearByPrefix("cache:stat:");
            localCacheService.clearByPrefix("cache:traffic:");
            localCacheService.clearByPrefix("cache:attack:");
            
            log.info("深度清理临时缓存完成");
        } catch (Exception e) {
            log.error("深度清理缓存失败：", e);
        }
    }
}
