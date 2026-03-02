package com.network.gateway.cache;

import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.util.LocalCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 流量临时缓存
 * 存储近1小时的流量监控数据，用于实时查询和分析
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class TrafficTempCache {

    private static final Logger logger = LoggerFactory.getLogger(TrafficTempCache.class);

    /**
     * 流量缓存实例
     */
    private LocalCacheUtil<String, TrafficMonitorDTO> trafficCache;

    /**
     * 初始化缓存
     */
    @PostConstruct
    public void init() {
        this.trafficCache = new LocalCacheUtil<>(
                "TrafficTempCache",
                GatewayCacheConstant.TRAFFIC_CACHE_EXPIRE_TIME,
                GatewayCacheConstant.CACHE_CLEANUP_INTERVAL
        );
        logger.info("流量临时缓存初始化完成");
    }

    /**
     * 存储流量数据
     *
     * @param requestId 请求ID
     * @param trafficDTO 流量监控DTO
     */
    public void putTraffic(String requestId, TrafficMonitorDTO trafficDTO) {
        if (requestId == null || trafficDTO == null) {
            logger.warn("尝试存储空的流量数据，操作被忽略");
            return;
        }

        String cacheKey = buildCacheKey(requestId);
        trafficCache.put(cacheKey, trafficDTO);
        logger.debug("存储流量数据: 请求ID[{}]", requestId);
    }

    /**
     * 获取流量数据
     *
     * @param requestId 请求ID
     * @return TrafficMonitorDTO
     */
    public TrafficMonitorDTO getTraffic(String requestId) {
        if (requestId == null) {
            return null;
        }

        String cacheKey = buildCacheKey(requestId);
        TrafficMonitorDTO trafficDTO = trafficCache.get(cacheKey);
        
        if (trafficDTO != null) {
            logger.debug("获取到流量数据: 请求ID[{}]", requestId);
        } else {
            logger.debug("未找到流量数据: 请求ID[{}]", requestId);
        }
        
        return trafficDTO;
    }

    /**
     * 移除流量数据
     *
     * @param requestId 请求ID
     * @return 被移除的数据
     */
    public TrafficMonitorDTO removeTraffic(String requestId) {
        if (requestId == null) {
            return null;
        }

        String cacheKey = buildCacheKey(requestId);
        TrafficMonitorDTO removed = trafficCache.remove(cacheKey);
        
        if (removed != null) {
            logger.debug("移除流量数据: 请求ID[{}]", requestId);
        }
        
        return removed;
    }

    /**
     * 检查流量数据是否存在
     *
     * @param requestId 请求ID
     * @return true表示存在
     */
    public boolean containsTraffic(String requestId) {
        if (requestId == null) {
            return false;
        }

        String cacheKey = buildCacheKey(requestId);
        return trafficCache.containsKey(cacheKey);
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存中的流量数量
     */
    public int getSize() {
        return trafficCache.size();
    }

    /**
     * 清理过期的流量数据
     */
    public void cleanupExpired() {
        trafficCache.cleanupExpiredEntries();
        logger.debug("执行流量缓存清理");
    }

    /**
     * 清空所有流量数据
     */
    public void clearAll() {
        trafficCache.clear();
        logger.info("清空所有流量缓存数据");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public String getStats() {
        return trafficCache.getStats();
    }

    /**
     * 构建缓存键
     *
     * @param requestId 请求ID
     * @return 缓存键
     */
    private String buildCacheKey(String requestId) {
        return GatewayCacheConstant.TRAFFIC_CACHE_KEY_PREFIX + requestId;
    }

    /**
     * 获取所有缓存的请求ID
     *
     * @return 请求ID集合
     */
    public java.util.Set<String> getAllRequestIds() {
        return trafficCache.keySet().stream()
                .map(key -> key.substring(GatewayCacheConstant.TRAFFIC_CACHE_KEY_PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取最近的流量数据（按时间倒序）
     *
     * @param limit 限制数量
     * @return 流量数据列表
     */
    public java.util.List<TrafficMonitorDTO> getRecentTraffics(int limit) {
        return trafficCache.values().stream()
                .sorted((t1, t2) -> Long.compare(t2.getRequestTimestamp(), t1.getRequestTimestamp()))
                .limit(limit)
                .toList();
    }

    /**
     * 关闭缓存
     */
    @PreDestroy
    public void destroy() {
        if (trafficCache != null) {
            trafficCache.shutdown();
            logger.info("流量临时缓存已关闭");
        }
    }

    /**
     * 获取指定时间段内的流量统计
     *
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 统计信息
     */
    public TrafficStatistics getTrafficStatistics(long startTime, long endTime) {
        long totalRequests = 0;
        long abnormalRequests = 0;
        long totalProcessingTime = 0;
        int successfulRequests = 0;

        for (TrafficMonitorDTO traffic : trafficCache.values()) {
            long requestTime = traffic.getRequestTimestamp();
            if (requestTime >= startTime && requestTime <= endTime) {
                totalRequests++;
                
                if (Boolean.TRUE.equals(traffic.getAbnormalTraffic())) {
                    abnormalRequests++;
                }
                
                if (traffic.getProcessingTime() != null) {
                    totalProcessingTime += traffic.getProcessingTime();
                }
                
                if (Boolean.TRUE.equals(traffic.getSuccess())) {
                    successfulRequests++;
                }
            }
        }

        double avgProcessingTime = totalRequests > 0 ? 
                (double) totalProcessingTime / totalRequests : 0.0;
        double successRate = totalRequests > 0 ? 
                (double) successfulRequests / totalRequests * 100 : 0.0;
        double abnormalRate = totalRequests > 0 ? 
                (double) abnormalRequests / totalRequests * 100 : 0.0;

        return new TrafficStatistics(totalRequests, abnormalRequests, 
                                   avgProcessingTime, successRate, abnormalRate);
    }

    /**
     * 流量统计信息内部类
     */
    public static class TrafficStatistics {
        private final long totalRequests;
        private final long abnormalRequests;
        private final double averageProcessingTime;
        private final double successRate;
        private final double abnormalRate;

        public TrafficStatistics(long totalRequests, long abnormalRequests,
                               double averageProcessingTime, double successRate, double abnormalRate) {
            this.totalRequests = totalRequests;
            this.abnormalRequests = abnormalRequests;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.abnormalRate = abnormalRate;
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getAbnormalRequests() { return abnormalRequests; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public double getAbnormalRate() { return abnormalRate; }

        @Override
        public String toString() {
            return String.format("总请求数:%d 异常请求数:%d 平均处理时间:%.2fms 成功率:%.2f%% 异常率:%.2f%%",
                    totalRequests, abnormalRequests, averageProcessingTime, successRate, abnormalRate);
        }
    }
}