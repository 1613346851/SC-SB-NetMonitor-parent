package com.network.gateway.handler;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SamplingPushHandler implements TrafficPushHandler {

    private static final Logger logger = LoggerFactory.getLogger(SamplingPushHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_SAMPLING_RATE = 10;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    private final ConcurrentHashMap<String, AtomicInteger> samplingCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SamplingStats> statsMap = new ConcurrentHashMap<>();

    @Override
    public void handle(TrafficMonitorDTO trafficDTO) {
        String sourceIp = trafficDTO.getSourceIp();
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(sourceIp));
        int samplingRate = getSamplingRate();
        
        AtomicInteger counter = samplingCounters.computeIfAbsent(sourceIp, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();
        
        SamplingStats stats = statsMap.computeIfAbsent(sourceIp, k -> new SamplingStats());
        stats.increment();
        stats.addProcessingTime(trafficDTO.getProcessingTime() != null ? trafficDTO.getProcessingTime() : 0);
        
        if (trafficDTO.getResponseStatus() != null && trafficDTO.getResponseStatus() >= 400) {
            stats.incrementError();
        }
        
        if (currentCount % samplingRate == 1) {
            try {
                trafficDTO.setRequestCount(currentCount);
                trafficDTO.setStateTag(stateTag);
                trafficDTO.setIsAggregated(true);
                trafficDTO.setAggregateStartTime(formatDateTime(stats.getStartTime()));
                trafficDTO.setAggregateEndTime(formatDateTime(LocalDateTime.now()));
                trafficDTO.setErrorCount(stats.getErrorCount());
                trafficDTO.setAvgProcessingTime(stats.getAverageProcessingTime());
                
                trafficClient.pushTraffic(trafficDTO);
                logger.debug("采样推送成功：ip={} count={} 采样率=1/{}", 
                    sourceIp, currentCount, samplingRate);
            } catch (Exception e) {
                logger.warn("采样推送失败：ip={}, error={}", sourceIp, e.getMessage());
            }
        } else {
            logger.debug("采样推送跳过：ip={} count={} 采样率=1/{}", 
                sourceIp, currentCount, samplingRate);
        }
    }

    @Override
    public String getStrategyName() {
        return "sampling";
    }

    @Override
    public void flush() {
        logger.debug("采样推送处理器flush: 清理计数器");
        samplingCounters.clear();
        statsMap.clear();
    }

    private int getSamplingRate() {
        return DEFAULT_SAMPLING_RATE;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    public int getIpCount() {
        return samplingCounters.size();
    }

    public long getTotalRequestCount() {
        return samplingCounters.values().stream()
            .mapToLong(AtomicInteger::get)
            .sum();
    }

    public String getStats() {
        return String.format("采样IP数:%d 总请求数:%d", getIpCount(), getTotalRequestCount());
    }

    private static class SamplingStats {
        private final LocalDateTime startTime = LocalDateTime.now();
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicLong totalProcessingTime = 
            new java.util.concurrent.atomic.AtomicLong(0);

        public void increment() {
            count.incrementAndGet();
        }

        public void incrementError() {
            errorCount.incrementAndGet();
        }

        public void addProcessingTime(long time) {
            totalProcessingTime.addAndGet(time);
        }

        public LocalDateTime getStartTime() { return startTime; }
        public int getCount() { return count.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public long getAverageProcessingTime() { 
            int c = count.get();
            return c > 0 ? totalProcessingTime.get() / c : 0; 
        }
    }
}
