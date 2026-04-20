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

@Component
public class DelayedBatchPushHandler implements TrafficPushHandler {

    private static final Logger logger = LoggerFactory.getLogger(DelayedBatchPushHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_BATCH_WINDOW_MS = 5000;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    private final ConcurrentHashMap<String, DelayedBatch> batchMap = new ConcurrentHashMap<>();

    @Override
    public void handle(TrafficMonitorDTO trafficDTO) {
        String batchKey = buildBatchKey(trafficDTO);
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(trafficDTO.getSourceIp()));
        
        DelayedBatch batch = batchMap.compute(batchKey, (key, existing) -> {
            if (existing == null || existing.isExpired(DEFAULT_BATCH_WINDOW_MS)) {
                DelayedBatch newBatch = new DelayedBatch();
                newBatch.setSourceIp(trafficDTO.getSourceIp());
                newBatch.setRequestUri(trafficDTO.getRequestUri());
                newBatch.setHttpMethod(trafficDTO.getHttpMethod());
                newBatch.setUserAgent(truncateUserAgent(trafficDTO.getUserAgent()));
                newBatch.setContentType(trafficDTO.getContentType());
                newBatch.setStartTime(LocalDateTime.now());
                newBatch.setStateTag(stateTag);
                newBatch.addSample(trafficDTO);
                return newBatch;
            }
            existing.addSample(trafficDTO);
            return existing;
        });
        
        logger.debug("延迟批量推送：流量数据已缓存 key={} count={}", batchKey, batch.getCount());
    }

    @Override
    public String getStrategyName() {
        return "delayed_batch";
    }

    @Override
    public void flush() {
        List<DelayedBatch> toFlush = new ArrayList<>();
        List<String> flushedKeys = new ArrayList<>();
        
        for (Map.Entry<String, DelayedBatch> entry : batchMap.entrySet()) {
            DelayedBatch batch = entry.getValue();
            if (batch.isExpired(DEFAULT_BATCH_WINDOW_MS) && batch.getCount() > 0) {
                toFlush.add(batch);
                flushedKeys.add(entry.getKey());
            }
        }
        
        for (String key : flushedKeys) {
            batchMap.remove(key);
        }
        
        if (toFlush.isEmpty()) {
            return;
        }
        
        logger.info("延迟批量推送：开始推送{}条聚合数据", toFlush.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (DelayedBatch batch : toFlush) {
            try {
                TrafficMonitorDTO dto = convertBatchToDTO(batch);
                trafficClient.pushTraffic(dto);
                successCount++;
            } catch (Exception e) {
                failCount++;
                logger.warn("延迟批量推送失败: ip={}, uri={}, error={}", 
                    batch.getSourceIp(), batch.getRequestUri(), e.getMessage());
            }
        }
        
        logger.info("延迟批量推送完成: 成功{}条, 失败{}条", successCount, failCount);
    }

    private String buildBatchKey(TrafficMonitorDTO traffic) {
        String userAgentHash = traffic.getUserAgent() != null ? 
            String.valueOf(traffic.getUserAgent().hashCode()) : "";
        
        return String.format("%s|%s|%s|%s",
            traffic.getSourceIp(),
            traffic.getRequestUri(),
            traffic.getHttpMethod(),
            userAgentHash
        );
    }

    private TrafficMonitorDTO convertBatchToDTO(DelayedBatch batch) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        dto.setSourceIp(batch.getSourceIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri(batch.getRequestUri());
        dto.setHttpMethod(batch.getHttpMethod());
        dto.setUserAgent(batch.getUserAgent());
        dto.setContentType(batch.getContentType());
        dto.setRequestCount(batch.getCount());
        dto.setErrorCount(batch.getErrorCount());
        dto.setAvgProcessingTime(batch.getAverageProcessingTime());
        dto.setStateTag(batch.getStateTag());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(formatDateTime(batch.getStartTime()));
        dto.setAggregateEndTime(formatDateTime(batch.getLastUpdateTime()));
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

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
    }

    public int getBatchCount() {
        return batchMap.size();
    }

    public long getTotalRequestCount() {
        return batchMap.values().stream()
            .mapToLong(DelayedBatch::getCount)
            .sum();
    }

    public String getStats() {
        return String.format("延迟批量条目数:%d 总请求数:%d", getBatchCount(), getTotalRequestCount());
    }

    private static class DelayedBatch {
        private String sourceIp;
        private String requestUri;
        private String httpMethod;
        private String userAgent;
        private String contentType;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdateTime;
        private String stateTag;
        private int count = 0;
        private int errorCount = 0;
        private long totalProcessingTime = 0;

        public void addSample(TrafficMonitorDTO traffic) {
            this.count++;
            this.lastUpdateTime = LocalDateTime.now();
            
            if (traffic.getResponseStatus() != null && traffic.getResponseStatus() >= 400) {
                this.errorCount++;
            }
            
            if (traffic.getProcessingTime() != null) {
                this.totalProcessingTime += traffic.getProcessingTime();
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
        public int getCount() { return count; }
        public int getErrorCount() { return errorCount; }
        public long getAverageProcessingTime() { 
            return count > 0 ? totalProcessingTime / count : 0; 
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
