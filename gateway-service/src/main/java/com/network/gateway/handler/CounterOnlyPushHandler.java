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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CounterOnlyPushHandler implements TrafficPushHandler {

    private static final Logger logger = LoggerFactory.getLogger(CounterOnlyPushHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_COUNTER_WINDOW_MS = 10000;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    private final ConcurrentHashMap<String, TrafficCounter> counterMap = new ConcurrentHashMap<>();

    @Override
    public void handle(TrafficMonitorDTO trafficDTO) {
        String counterKey = buildCounterKey(trafficDTO);
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(trafficDTO.getSourceIp()));
        
        TrafficCounter counter = counterMap.compute(counterKey, (key, existing) -> {
            if (existing == null || existing.isExpired(DEFAULT_COUNTER_WINDOW_MS)) {
                TrafficCounter newCounter = new TrafficCounter();
                newCounter.setSourceIp(trafficDTO.getSourceIp());
                newCounter.setStateTag(stateTag);
                newCounter.setStartTime(LocalDateTime.now());
                return newCounter;
            }
            return existing;
        });
        
        counter.increment();
        counter.addProcessingTime(trafficDTO.getProcessingTime() != null ? trafficDTO.getProcessingTime() : 0);
        
        if (trafficDTO.getResponseStatus() != null && trafficDTO.getResponseStatus() >= 400) {
            counter.incrementError();
        }
        
        logger.debug("计数推送：已统计 ip={} count={}", trafficDTO.getSourceIp(), counter.getCount());
    }

    @Override
    public String getStrategyName() {
        return "counter_only";
    }

    @Override
    public void flush() {
        List<TrafficCounter> toFlush = new ArrayList<>();
        List<String> flushedKeys = new ArrayList<>();
        
        for (Map.Entry<String, TrafficCounter> entry : counterMap.entrySet()) {
            TrafficCounter counter = entry.getValue();
            if (counter.isExpired(DEFAULT_COUNTER_WINDOW_MS) && counter.getCount() > 0) {
                toFlush.add(counter);
                flushedKeys.add(entry.getKey());
            }
        }
        
        for (String key : flushedKeys) {
            counterMap.remove(key);
        }
        
        if (toFlush.isEmpty()) {
            return;
        }
        
        logger.info("计数推送：开始推送{}条统计数据", toFlush.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (TrafficCounter counter : toFlush) {
            try {
                TrafficMonitorDTO dto = convertCounterToDTO(counter);
                trafficClient.pushTraffic(dto);
                successCount++;
            } catch (Exception e) {
                failCount++;
                logger.warn("计数推送失败: ip={}, error={}", counter.getSourceIp(), e.getMessage());
            }
        }
        
        logger.info("计数推送完成: 成功{}条, 失败{}条", successCount, failCount);
    }

    private String buildCounterKey(TrafficMonitorDTO traffic) {
        return traffic.getSourceIp();
    }

    private TrafficMonitorDTO convertCounterToDTO(TrafficCounter counter) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        dto.setSourceIp(counter.getSourceIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri("/__counter__");
        dto.setHttpMethod("COUNTER");
        dto.setUserAgent("");
        dto.setContentType("");
        dto.setRequestCount(counter.getCount());
        dto.setErrorCount(counter.getErrorCount());
        dto.setAvgProcessingTime(counter.getAverageProcessingTime());
        dto.setStateTag(counter.getStateTag());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(formatDateTime(counter.getStartTime()));
        dto.setAggregateEndTime(formatDateTime(counter.getLastUpdateTime()));
        dto.setRequestId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatDateTime(LocalDateTime.now()));
        dto.setSuccess(true);
        dto.setResponseStatus(200);
        return dto;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    public int getCounterCount() {
        return counterMap.size();
    }

    public long getTotalRequestCount() {
        return counterMap.values().stream()
            .mapToLong(TrafficCounter::getCount)
            .sum();
    }

    public String getStats() {
        return String.format("计数器条目数:%d 总请求数:%d", getCounterCount(), getTotalRequestCount());
    }

    private static class TrafficCounter {
        private String sourceIp;
        private String stateTag;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdateTime;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);

        public void increment() {
            count.incrementAndGet();
            lastUpdateTime = LocalDateTime.now();
        }

        public void incrementError() {
            errorCount.incrementAndGet();
        }

        public void addProcessingTime(long time) {
            totalProcessingTime.addAndGet(time);
        }

        public String getSourceIp() { return sourceIp; }
        public String getStateTag() { return stateTag; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public int getCount() { return count.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public long getAverageProcessingTime() { 
            int c = count.get();
            return c > 0 ? totalProcessingTime.get() / c : 0; 
        }

        public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
        public void setStateTag(String stateTag) { this.stateTag = stateTag; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public boolean isExpired(long windowMs) {
            if (lastUpdateTime == null) {
                return true;
            }
            return LocalDateTime.now().isAfter(lastUpdateTime.plusNanos(windowMs * 1_000_000));
        }
    }
}
