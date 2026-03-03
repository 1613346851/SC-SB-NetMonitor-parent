package com.network.gateway.cache;

import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.util.LocalCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * IP黑名单缓存
 * 存储被拉黑的IP地址及其过期时间
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class IpBlacklistCache {

    private static final Logger logger = LoggerFactory.getLogger(IpBlacklistCache.class);

    /**
     * 黑名单缓存实例
     */
    private LocalCacheUtil<String, Long> blacklistCache;

    /**
     * 初始化缓存
     */
    @PostConstruct
    public void init() {
        this.blacklistCache = new LocalCacheUtil<>(
                "IpBlacklistCache",
                GatewayCacheConstant.BLACKLIST_CACHE_EXPIRE_TIME,
                GatewayCacheConstant.CACHE_CLEANUP_INTERVAL
        );
        logger.info("IP黑名单缓存初始化完成");
    }

    /**
     * 添加IP到黑名单
     *
     * @param ip IP地址
     * @param expireTime 过期时间戳（毫秒）
     * @return true表示添加成功
     */
    public boolean addToBlacklist(String ip, Long expireTime) {
        if (ip == null || ip.isEmpty() || expireTime == null) {
            logger.warn("尝试添加无效的黑名单条目: IP[{}], 过期时间[{}]", ip, expireTime);
            return false;
        }

        if (System.currentTimeMillis() > expireTime) {
            logger.warn("尝试添加已过期的黑名单条目: IP[{}], 过期时间[{}]", ip, expireTime);
            return false;
        }

        String cacheKey = buildCacheKey(ip);
        blacklistCache.put(cacheKey, expireTime);
        logger.info("IP[{}]已加入黑名单，过期时间[{}]", ip, expireTime);
        return true;
    }

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址
     * @return true表示移除成功
     */
    public boolean removeFromBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String cacheKey = buildCacheKey(ip);
        Long removed = blacklistCache.remove(cacheKey);
        
        if (removed != null) {
            logger.info("IP[{}]已从黑名单中移除", ip);
            return true;
        }
        
        return false;
    }

    /**
     * 检查IP是否在黑名单中且未过期
     *
     * @param ip IP地址
     * @return true表示在黑名单中
     */
    public boolean isInBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String cacheKey = buildCacheKey(ip);
        Long expireTime = blacklistCache.get(cacheKey);
        
        if (expireTime == null) {
            return false;
        }

        // 检查是否过期
        if (System.currentTimeMillis() > expireTime) {
            // 发现过期项，自动清理
            blacklistCache.remove(cacheKey);
            logger.debug("发现过期的黑名单条目，已自动清理: IP[{}]", ip);
            return false;
        }

        return true;
    }

    /**
     * 获取IP的黑名单过期时间
     *
     * @param ip IP地址
     * @return 过期时间戳，如果不在黑名单中返回null
     */
    public Long getBlacklistExpireTime(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        String cacheKey = buildCacheKey(ip);
        return blacklistCache.get(cacheKey);
    }

    /**
     * 获取IP在黑名单中的剩余时间
     *
     * @param ip IP地址
     * @return 剩余时间（毫秒），如果不在黑名单中返回-1
     */
    public long getRemainingTime(String ip) {
        Long expireTime = getBlacklistExpireTime(ip);
        if (expireTime == null) {
            return -1;
        }
        
        long remaining = expireTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 获取黑名单大小
     *
     * @return 黑名单中的IP数量
     */
    public int getSize() {
        return blacklistCache.size();
    }

    /**
     * 清理过期的黑名单条目
     */
    public void cleanupExpired() {
        blacklistCache.cleanupExpiredEntries();
        logger.debug("执行黑名单缓存清理");
    }

    /**
     * 清空所有黑名单
     */
    public void clearAll() {
        blacklistCache.clear();
        logger.info("清空所有黑名单数据");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public String getStats() {
        return blacklistCache.getStats();
    }

    /**
     * 获取所有黑名单IP
     *
     * @return IP地址集合
     */
    public java.util.Set<String> getAllBlacklistedIps() {
        return blacklistCache.keySet().stream()
                .map(key -> key.substring(GatewayCacheConstant.BLACKLIST_CACHE_KEY_PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取即将过期的黑名单（剩余时间小于指定阈值）
     *
     * @param threshold 阈值（毫秒）
     * @return 即将过期的IP集合
     */
    public java.util.Set<String> getExpiringSoon(long threshold) {
        java.util.Set<String> expiringIps = new java.util.HashSet<>();
        long currentTime = System.currentTimeMillis();

        for (String cacheKey : blacklistCache.keySet()) {
            Long expireTime = blacklistCache.get(cacheKey);
            if (expireTime != null) {
                long remainingTime = expireTime - currentTime;
                if (remainingTime > 0 && remainingTime < threshold) {
                    String ip = cacheKey.substring(GatewayCacheConstant.BLACKLIST_CACHE_KEY_PREFIX.length());
                    expiringIps.add(ip);
                }
            }
        }

        return expiringIps;
    }

    /**
     * 批量添加黑名单
     *
     * @param ipExpireMap IP地址和过期时间的映射
     * @return 成功添加的数量
     */
    public int batchAddToBlacklist(java.util.Map<String, Long> ipExpireMap) {
        if (ipExpireMap == null || ipExpireMap.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        long currentTime = System.currentTimeMillis();

        for (java.util.Map.Entry<String, Long> entry : ipExpireMap.entrySet()) {
            String ip = entry.getKey();
            Long expireTime = entry.getValue();

            if (ip != null && !ip.isEmpty() && expireTime != null && expireTime > currentTime) {
                String cacheKey = buildCacheKey(ip);
                blacklistCache.put(cacheKey, expireTime);
                successCount++;
            }
        }

        if (successCount > 0) {
            logger.info("批量添加{}个IP到黑名单", successCount);
        }

        return successCount;
    }

    /**
     * 批量移除黑名单
     *
     * @param ips IP地址集合
     * @return 成功移除的数量
     */
    public int batchRemoveFromBlacklist(java.util.Set<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return 0;
        }

        int removedCount = 0;
        for (String ip : ips) {
            if (removeFromBlacklist(ip)) {
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("批量移除{}个IP从黑名单", removedCount);
        }

        return removedCount;
    }

    /**
     * 构建缓存键
     *
     * @param ip IP地址
     * @return 缓存键
     */
    private String buildCacheKey(String ip) {
        return GatewayCacheConstant.BLACKLIST_CACHE_KEY_PREFIX + ip;
    }

    /**
     * 延长黑名单过期时间
     *
     * @param ip IP地址
     * @param additionalTime 额外延长时间（毫秒）
     * @return true表示延长成功
     */
    public boolean extendBlacklistTime(String ip, long additionalTime) {
        if (ip == null || ip.isEmpty() || additionalTime <= 0) {
            return false;
        }

        String cacheKey = buildCacheKey(ip);
        Long currentExpireTime = blacklistCache.get(cacheKey);

        if (currentExpireTime != null) {
            long newExpireTime = currentExpireTime + additionalTime;
            blacklistCache.put(cacheKey, newExpireTime);
            logger.info("延长IP[{}]黑名单时间{}毫秒，新过期时间[{}]", ip, additionalTime, newExpireTime);
            return true;
        }

        return false;
    }

    /**
     * 关闭缓存
     */
    @PreDestroy
    public void destroy() {
        if (blacklistCache != null) {
            blacklistCache.shutdown();
            logger.info("IP黑名单缓存已关闭");
        }
    }
}