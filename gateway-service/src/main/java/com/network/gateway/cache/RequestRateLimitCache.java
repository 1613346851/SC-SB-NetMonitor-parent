package com.network.gateway.cache;

import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.util.LocalCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 请求限流缓存
 * 基于时间窗口的请求计数，实现精确的限流控制
 * 同时支持监测服务动态下发单 IP 限流阈值
 */
@Component
public class RequestRateLimitCache {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimitCache.class);

    private static final String CUSTOM_THRESHOLD_CACHE_KEY_PREFIX =
            GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX + "threshold:";

    /**
     * 请求计数缓存
     */
    private LocalCacheUtil<String, AtomicInteger> rateLimitCache;

    /**
     * 单 IP 自定义限流阈值缓存
     */
    private LocalCacheUtil<String, Integer> customThresholdCache;

    /**
     * 初始化缓存
     */
    @PostConstruct
    public void init() {
        this.rateLimitCache = new LocalCacheUtil<>(
                "RequestRateLimitCache",
                GatewayCacheConstant.RATE_LIMIT_CACHE_EXPIRE_TIME,
                GatewayCacheConstant.CACHE_CLEANUP_INTERVAL
        );
        this.customThresholdCache = new LocalCacheUtil<>(
                "RequestRateLimitThresholdCache",
                GatewayCacheConstant.BLACKLIST_CACHE_EXPIRE_TIME,
                GatewayCacheConstant.CACHE_CLEANUP_INTERVAL
        );
        logger.info("请求限流缓存初始化完成");
    }

    /**
     * 检查并增加请求计数
     *
     * @param ip IP地址
     * @param threshold 限流阈值
     * @return true表示超出限流阈值，需要限流
     */
    public boolean checkAndIncrement(String ip, int threshold) {
        if (ip == null || ip.isEmpty() || threshold <= 0) {
            return false;
        }

        String cacheKey = buildCacheKey(ip);
        AtomicInteger counter = rateLimitCache.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));

        int currentCount = counter.incrementAndGet();
        boolean shouldLimit = currentCount > threshold;

        if (shouldLimit) {
            logger.debug("IP[{}]请求频率超出限制: 当前{}次/秒 > 阈值{}次/秒", ip, currentCount, threshold);
        }

        return shouldLimit;
    }

    /**
     * 获取指定IP的当前请求数
     */
    public int getCurrentRequestCount(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }

        String cacheKey = buildCacheKey(ip);
        AtomicInteger counter = rateLimitCache.get(cacheKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 为指定 IP 设置自定义限流阈值
     */
    public void setCustomThreshold(String ip, int threshold, Long expireTimestamp) {
        if (ip == null || ip.isEmpty() || threshold <= 0) {
            return;
        }

        long ttl = GatewayCacheConstant.BLACKLIST_CACHE_EXPIRE_TIME;
        if (expireTimestamp != null) {
            ttl = Math.max(GatewayCacheConstant.RATE_LIMIT_CACHE_EXPIRE_TIME,
                    expireTimestamp - System.currentTimeMillis());
        }

        customThresholdCache.put(buildThresholdKey(ip), threshold, ttl);
        logger.info("设置IP[{}]动态限流阈值成功：{}次/秒，有效期{}ms", ip, threshold, ttl);
    }

    /**
     * 获取指定 IP 的自定义限流阈值
     */
    public Integer getCustomThreshold(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        return customThresholdCache.get(buildThresholdKey(ip));
    }

    /**
     * 获取指定 IP 的生效阈值
     */
    public int getEffectiveThreshold(String ip, int defaultThreshold) {
        Integer customThreshold = getCustomThreshold(ip);
        return customThreshold != null && customThreshold > 0 ? customThreshold : defaultThreshold;
    }

    /**
     * 移除指定 IP 的自定义阈值
     */
    public void removeCustomThreshold(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        customThresholdCache.remove(buildThresholdKey(ip));
        logger.debug("移除IP[{}]动态限流阈值", ip);
    }

    /**
     * 获取动态阈值配置数量
     */
    public int getCustomThresholdCount() {
        return customThresholdCache.size();
    }

    /**
     * 重置指定IP的请求计数
     */
    public void resetRequestCount(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        String cacheKey = buildCacheKey(ip);
        rateLimitCache.remove(cacheKey);
        logger.debug("重置IP[{}]的请求计数", ip);
    }

    /**
     * 获取缓存大小
     */
    public int getSize() {
        return rateLimitCache.size();
    }

    /**
     * 清理过期的限流记录
     */
    public void cleanupExpired() {
        rateLimitCache.cleanupExpiredEntries();
        customThresholdCache.cleanupExpiredEntries();
        logger.debug("执行限流缓存清理");
    }

    /**
     * 清空所有限流记录
     */
    public void clearAll() {
        rateLimitCache.clear();
        customThresholdCache.clear();
        logger.info("清空所有限流缓存数据");
    }

    /**
     * 获取缓存统计信息
     */
    public String getStats() {
        return String.format("请求计数[%s] 动态阈值[%s]", rateLimitCache.getStats(), customThresholdCache.getStats());
    }

    /**
     * 获取所有活跃的IP地址
     */
    public Set<String> getActiveIps() {
        return rateLimitCache.keySet().stream()
                .map(key -> {
                    String suffix = key.substring(GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX.length());
                    int delimiterIndex = suffix.lastIndexOf(':');
                    return delimiterIndex > -1 ? suffix.substring(0, delimiterIndex) : suffix;
                })
                .collect(Collectors.toSet());
    }

    /**
     * 获取高频率请求的IP（超过阈值一定比例）
     */
    public Set<String> getHighFrequencyIps(int threshold, double ratio) {
        Set<String> highFreqIps = new HashSet<>();
        int minCount = (int) (threshold * ratio);

        for (String cacheKey : rateLimitCache.keySet()) {
            AtomicInteger counter = rateLimitCache.get(cacheKey);
            if (counter != null && counter.get() >= minCount) {
                String suffix = cacheKey.substring(GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX.length());
                int delimiterIndex = suffix.lastIndexOf(':');
                String ip = delimiterIndex > -1 ? suffix.substring(0, delimiterIndex) : suffix;
                highFreqIps.add(ip);
            }
        }

        return highFreqIps;
    }

    /**
     * 批量重置多个IP的请求计数
     */
    public int batchResetRequestCount(Set<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return 0;
        }

        int resetCount = 0;
        for (String ip : ips) {
            resetRequestCount(ip);
            resetCount++;
        }

        if (resetCount > 0) {
            logger.debug("批量重置{}个IP的请求计数", resetCount);
        }

        return resetCount;
    }

    /**
     * 获取限流统计信息
     */
    public RateLimitStatistics getStatistics(int threshold) {
        int totalActiveIps = 0;
        int exceededIps = 0;
        int totalRequests = 0;
        int maxRequests = 0;

        for (String cacheKey : rateLimitCache.keySet()) {
            AtomicInteger counter = rateLimitCache.get(cacheKey);
            if (counter != null) {
                totalActiveIps++;
                int count = counter.get();
                totalRequests += count;
                maxRequests = Math.max(maxRequests, count);

                if (count > threshold) {
                    exceededIps++;
                }
            }
        }

        double avgRequests = totalActiveIps > 0 ? (double) totalRequests / totalActiveIps : 0.0;
        double exceedRate = totalActiveIps > 0 ? (double) exceededIps / totalActiveIps * 100 : 0.0;

        return new RateLimitStatistics(totalActiveIps, exceededIps, totalRequests,
                maxRequests, avgRequests, exceedRate);
    }

    private String buildCacheKey(String ip) {
        long window = System.currentTimeMillis() / 1000;
        return GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX + ip + ":" + window;
    }

    private String buildThresholdKey(String ip) {
        return CUSTOM_THRESHOLD_CACHE_KEY_PREFIX + ip;
    }

    /**
     * 关闭缓存
     */
    @PreDestroy
    public void destroy() {
        if (rateLimitCache != null) {
            rateLimitCache.shutdown();
        }
        if (customThresholdCache != null) {
            customThresholdCache.shutdown();
        }
        logger.info("请求限流缓存已关闭");
    }

    /**
     * 限流统计信息内部类
     */
    public static class RateLimitStatistics {
        private final int activeIpCount;
        private final int exceededIpCount;
        private final int totalRequestCount;
        private final int maxRequestCount;
        private final double averageRequestCount;
        private final double exceedRate;

        public RateLimitStatistics(int activeIpCount, int exceededIpCount, int totalRequestCount,
                                   int maxRequestCount, double averageRequestCount, double exceedRate) {
            this.activeIpCount = activeIpCount;
            this.exceededIpCount = exceededIpCount;
            this.totalRequestCount = totalRequestCount;
            this.maxRequestCount = maxRequestCount;
            this.averageRequestCount = averageRequestCount;
            this.exceedRate = exceedRate;
        }

        public int getActiveIpCount() { return activeIpCount; }
        public int getExceededIpCount() { return exceededIpCount; }
        public int getTotalRequestCount() { return totalRequestCount; }
        public int getMaxRequestCount() { return maxRequestCount; }
        public double getAverageRequestCount() { return averageRequestCount; }
        public double getExceedRate() { return exceedRate; }

        @Override
        public String toString() {
            return String.format("活跃IP数:%d 超限IP数:%d 总请求数:%d 最大请求数:%d 平均请求数:%.2f 超限率:%.2f%%",
                    activeIpCount, exceededIpCount, totalRequestCount, maxRequestCount,
                    averageRequestCount, exceedRate);
        }
    }
}
