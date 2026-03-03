package com.network.gateway.cache;

import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.util.LocalCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 请求限流缓存
 * 基于时间窗口的请求计数，实现精确的限流控制
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class RequestRateLimitCache {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimitCache.class);

    /**
     * 限流缓存实例
     */
    private LocalCacheUtil<String, AtomicInteger> rateLimitCache;

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
        
        // 使用computeIfAbsent确保原子性操作
        AtomicInteger counter = rateLimitCache.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));
        
        // 增加计数并检查是否超出阈值
        int currentCount = counter.incrementAndGet();
        boolean shouldLimit = currentCount > threshold;
        
        if (shouldLimit) {
            logger.debug("IP[{}]请求频率超出限制: 当前{}次/秒 > 阈值{}次/秒", ip, currentCount, threshold);
        }
        
        return shouldLimit;
    }

    /**
     * 获取指定IP的当前请求数
     *
     * @param ip IP地址
     * @return 当前请求数
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
     * 重置指定IP的请求计数
     *
     * @param ip IP地址
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
     *
     * @return 当前活跃的IP数量
     */
    public int getSize() {
        return rateLimitCache.size();
    }

    /**
     * 清理过期的限流记录
     */
    public void cleanupExpired() {
        rateLimitCache.cleanupExpiredEntries();
        logger.debug("执行限流缓存清理");
    }

    /**
     * 清空所有限流记录
     */
    public void clearAll() {
        rateLimitCache.clear();
        logger.info("清空所有限流缓存数据");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public String getStats() {
        return rateLimitCache.getStats();
    }

    /**
     * 获取所有活跃的IP地址
     *
     * @return IP地址集合
     */
    public java.util.Set<String> getActiveIps() {
        return rateLimitCache.keySet().stream()
                .map(key -> key.substring(GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取高频率请求的IP（超过阈值一定比例）
     *
     * @param threshold 限流阈值
     * @param ratio 比例（0.0-1.0）
     * @return 高频IP集合
     */
    public java.util.Set<String> getHighFrequencyIps(int threshold, double ratio) {
        java.util.Set<String> highFreqIps = new java.util.HashSet<>();
        int minCount = (int) (threshold * ratio);

        for (String cacheKey : rateLimitCache.keySet()) {
            AtomicInteger counter = rateLimitCache.get(cacheKey);
            if (counter != null && counter.get() >= minCount) {
                String ip = cacheKey.substring(GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX.length());
                highFreqIps.add(ip);
            }
        }

        return highFreqIps;
    }

    /**
     * 批量重置多个IP的请求计数
     *
     * @param ips IP地址集合
     * @return 重置的IP数量
     */
    public int batchResetRequestCount(java.util.Set<String> ips) {
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
     *
     * @param threshold 限流阈值
     * @return 统计信息
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

    /**
     * 构建缓存键（包含时间戳以实现秒级窗口）
     *
     * @param ip IP地址
     * @return 缓存键
     */
    private String buildCacheKey(String ip) {
        // 使用秒级时间戳作为窗口标识
        long window = System.currentTimeMillis() / 1000;
        return GatewayCacheConstant.RATE_LIMIT_CACHE_KEY_PREFIX + ip + ":" + window;
    }

    /**
     * 关闭缓存
     */
    @PreDestroy
    public void destroy() {
        if (rateLimitCache != null) {
            rateLimitCache.shutdown();
            logger.info("请求限流缓存已关闭");
        }
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

        // Getters
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