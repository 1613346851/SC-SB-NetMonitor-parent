package com.network.gateway.handler;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class AggregatePushHandler implements TrafficPushHandler {

    private static final Logger logger = LoggerFactory.getLogger(AggregatePushHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_AGGREGATE_WINDOW_MS = 5000;
    private static final int MAX_SAMPLES = 5;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    private final ConcurrentHashMap<String, TrafficAggregate> aggregateMap = new ConcurrentHashMap<>();

    @Override
    public void handle(TrafficMonitorDTO trafficDTO) {
        String aggregateKey = buildAggregateKey(trafficDTO);
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(trafficDTO.getSourceIp()));
        
        TrafficAggregate aggregate = aggregateMap.compute(aggregateKey, (key, existing) -> {
            if (existing == null || existing.isExpired(DEFAULT_AGGREGATE_WINDOW_MS)) {
                TrafficAggregate newAggregate = new TrafficAggregate();
                newAggregate.setSourceIp(trafficDTO.getSourceIp());
                newAggregate.setRequestUri(trafficDTO.getRequestUri());
                newAggregate.setHttpMethod(trafficDTO.getHttpMethod());
                newAggregate.setUserAgent(truncateUserAgent(trafficDTO.getUserAgent()));
                newAggregate.setContentType(trafficDTO.getContentType());
                newAggregate.setStartTime(LocalDateTime.now());
                newAggregate.setStateTag(stateTag);
                return newAggregate;
            }
            return existing;
        });
        
        aggregate.increment();
        aggregate.addProcessingTime(trafficDTO.getProcessingTime() != null ? trafficDTO.getProcessingTime() : 0);
        
        if (trafficDTO.getResponseStatus() != null && trafficDTO.getResponseStatus() >= 400) {
            aggregate.incrementError();
        }
        
        aggregate.addSample(trafficDTO);
        
        logger.debug("聚合推送：已聚合 ip={} count={} samples={}", 
            trafficDTO.getSourceIp(), aggregate.getCount(), aggregate.getSampleCount());
    }

    @Override
    public String getStrategyName() {
        return "aggregate";
    }

    @Override
    public void flush() {
        List<TrafficAggregate> toFlush = new ArrayList<>();
        List<String> flushedKeys = new ArrayList<>();
        
        for (Map.Entry<String, TrafficAggregate> entry : aggregateMap.entrySet()) {
            TrafficAggregate aggregate = entry.getValue();
            if (aggregate.isExpired(DEFAULT_AGGREGATE_WINDOW_MS) && aggregate.getCount() > 0) {
                toFlush.add(aggregate);
                flushedKeys.add(entry.getKey());
            }
        }
        
        for (String key : flushedKeys) {
            aggregateMap.remove(key);
        }
        
        if (toFlush.isEmpty()) {
            return;
        }
        
        logger.info("聚合推送：开始推送{}条聚合数据", toFlush.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (TrafficAggregate aggregate : toFlush) {
            try {
                TrafficMonitorDTO dto = convertAggregateToDTO(aggregate);
                trafficClient.pushTraffic(dto);
                successCount++;
            } catch (Exception e) {
                failCount++;
                logger.warn("聚合推送失败: ip={}, uri={}, error={}", 
                    aggregate.getSourceIp(), aggregate.getRequestUri(), e.getMessage());
            }
        }
        
        logger.info("聚合推送完成: 成功{}条, 失败{}条", successCount, failCount);
    }

    private String buildAggregateKey(TrafficMonitorDTO traffic) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(nullToEmpty(traffic.getSourceIp())).append("|");
        keyBuilder.append(nullToEmpty(traffic.getTargetIp())).append("|");
        keyBuilder.append(nullToEmpty(traffic.getRequestUri())).append("|");
        keyBuilder.append(nullToEmpty(traffic.getHttpMethod())).append("|");
        keyBuilder.append(nullToEmpty(traffic.getContentType())).append("|");
        keyBuilder.append(normalizeHeaders(traffic.getRequestHeaders())).append("|");
        keyBuilder.append(normalizeBody(traffic.getRequestBody()));
        
        String rawKey = keyBuilder.toString();
        String hashedKey = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
        
        logger.debug("聚合Key生成: rawKey={}, hashedKey={}", rawKey.length() > 100 ? rawKey.substring(0, 100) + "..." : rawKey, hashedKey);
        
        return hashedKey;
    }
    
    private String normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(";"));
    }
    
    private String normalizeBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        if (body.length() > 500) {
            return DigestUtils.md5DigestAsHex(body.getBytes(StandardCharsets.UTF_8));
        }
        return body;
    }
    
    private String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

    private TrafficMonitorDTO convertAggregateToDTO(TrafficAggregate aggregate) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        dto.setSourceIp(aggregate.getSourceIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri(aggregate.getRequestUri());
        dto.setHttpMethod(aggregate.getHttpMethod());
        dto.setUserAgent(aggregate.getUserAgent());
        dto.setContentType(aggregate.getContentType());
        dto.setRequestCount(aggregate.getCount());
        dto.setErrorCount(aggregate.getErrorCount());
        dto.setAvgProcessingTime(aggregate.getAverageProcessingTime());
        dto.setPeakRps(aggregate.getPeakRps());
        dto.setStateTag(aggregate.getStateTag());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(formatDateTime(aggregate.getStartTime()));
        dto.setAggregateEndTime(formatDateTime(aggregate.getLastUpdateTime()));
        dto.setRequestId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatDateTime(LocalDateTime.now()));
        dto.setSuccess(true);
        dto.setResponseStatus(200);
        
        if (!aggregate.getSamples().isEmpty()) {
            TrafficMonitorDTO firstSample = aggregate.getSamples().get(0);
            dto.setRequestBody(firstSample.getRequestBody());
            dto.setRequestHeaders(firstSample.getRequestHeaders());
        }
        
        return dto;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
    }

    public int getAggregateCount() {
        return aggregateMap.size();
    }

    public long getTotalRequestCount() {
        return aggregateMap.values().stream()
            .mapToLong(TrafficAggregate::getCount)
            .sum();
    }

    public String getStats() {
        return String.format("聚合条目数:%d 总请求数:%d", getAggregateCount(), getTotalRequestCount());
    }

    private static class TrafficAggregate {
        private String sourceIp;
        private String requestUri;
        private String httpMethod;
        private String userAgent;
        private String contentType;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdateTime;
        private String stateTag;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final AtomicLong peakRps = new AtomicLong(0);
        private final List<TrafficMonitorDTO> samples = new ArrayList<>();
        private long lastSecondCount = 0;
        private LocalDateTime lastSecondTime = null;

        public void increment() {
            int newCount = count.incrementAndGet();
            lastUpdateTime = LocalDateTime.now();
            
            LocalDateTime now = LocalDateTime.now();
            if (lastSecondTime == null || !lastSecondTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                    .equals(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
                if (lastSecondTime != null) {
                    long rps = newCount - lastSecondCount;
                    if (rps > peakRps.get()) {
                        peakRps.set(rps);
                    }
                }
                lastSecondCount = newCount;
                lastSecondTime = now;
            }
        }

        public void incrementError() {
            errorCount.incrementAndGet();
        }

        public void addProcessingTime(long time) {
            totalProcessingTime.addAndGet(time);
        }

        public void addSample(TrafficMonitorDTO sample) {
            synchronized (samples) {
                if (samples.size() < MAX_SAMPLES) {
                    samples.add(sample);
                }
            }
        }

        public String getSourceIp() { return sourceIp; }
        public String getRequestUri() { return requestUri; }
        public String getHttpMethod() { return httpMethod; }
        public String getUserAgent() { return userAgent; }
        public String getContentType() { return contentType; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public String getStateTag() { return stateTag; }
        public int getCount() { return count.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public long getPeakRps() { return peakRps.get(); }
        public List<TrafficMonitorDTO> getSamples() { return samples; }
        public int getSampleCount() { return samples.size(); }
        public long getAverageProcessingTime() { 
            int c = count.get();
            return c > 0 ? totalProcessingTime.get() / c : 0; 
        }

        public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
        public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
        public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public void setStateTag(String stateTag) { this.stateTag = stateTag; }

        public boolean isExpired(long windowMs) {
            if (lastUpdateTime == null) {
                return true;
            }
            return LocalDateTime.now().isAfter(lastUpdateTime.plusNanos(windowMs * 1_000_000));
        }
    }
}
