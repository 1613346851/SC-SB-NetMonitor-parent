package com.network.gateway.traffic;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TrafficAggregateService {

    private static final Logger logger = LoggerFactory.getLogger(TrafficAggregateService.class);

    private final ConcurrentHashMap<String, IpAggregateBucket> aggregateBuckets = new ConcurrentHashMap<>();
    
    private final ExecutorService aggregateExecutor = Executors.newSingleThreadExecutor(
        r -> {
            Thread t = new Thread(r, "traffic-aggregate-worker");
            t.setDaemon(true);
            return t;
        }
    );
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "aggregate-period-checker");
            t.setDaemon(true);
            return t;
        }
    );
    
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private PushDegradationHandler degradationHandler;

    @Autowired
    private NetworkCongestionDetector congestionDetector;

    private long aggregateIntervalMs = 5000;

    @PostConstruct
    public void init() {
        aggregateIntervalMs = configCache.getTrafficPushAggregateIntervalMs();
        startPeriodicFlush();
        logger.info("流量聚合服务已初始化，聚合周期={}ms", aggregateIntervalMs);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("流量聚合服务正在关闭...");
        isShutdown.set(true);
        
        scheduler.shutdown();
        aggregateExecutor.shutdown();
        
        flushAll();
        
        try {
            if (!aggregateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                aggregateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aggregateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("流量聚合服务已关闭");
    }

    private void startPeriodicFlush() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushExpiredBuckets();
            } catch (Exception e) {
                logger.error("周期性刷新失败", e);
            }
        }, aggregateIntervalMs, aggregateIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void addSample(String ip, TrafficSample sample) {
        if (isShutdown.get()) {
            return;
        }
        
        IpAggregateBucket bucket = getOrCreateBucket(ip);
        bucket.addSample(sample);
        
        logger.debug("流量加入聚合桶: ip={}, uri={}, method={}, 当前聚合数={}", 
            ip, sample.getRequestUri(), sample.getHttpMethod(), bucket.getTotalCount());
    }

    public void onStateTransition(String ip, int fromState, int toState) {
        if (isShutdown.get()) {
            return;
        }
        
        IpAggregateBucket bucket = aggregateBuckets.get(ip);
        if (bucket != null && bucket.getTotalCount() > 0) {
            logger.info("状态转换触发聚合推送: ip={}, {} -> {}, 待推送流量数={}", 
                ip, IpAttackStateConstant.getStateNameZh(fromState), 
                IpAttackStateConstant.getStateNameZh(toState), bucket.getTotalCount());
            
            flushBucket(ip);
        }
    }

    private IpAggregateBucket getOrCreateBucket(String ip) {
        return aggregateBuckets.computeIfAbsent(ip, k -> new IpAggregateBucket(ip, aggregateIntervalMs));
    }

    private void flushExpiredBuckets() {
        long now = System.currentTimeMillis();
        List<String> toFlush = new ArrayList<>();
        
        for (Map.Entry<String, IpAggregateBucket> entry : aggregateBuckets.entrySet()) {
            IpAggregateBucket bucket = entry.getValue();
            if (bucket.shouldFlush(now)) {
                toFlush.add(entry.getKey());
            }
        }
        
        for (String ip : toFlush) {
            flushBucket(ip);
        }
        
        if (!toFlush.isEmpty()) {
            logger.info("周期性刷新: 刷新了{}个IP的聚合数据", toFlush.size());
        }
    }

    private void flushBucket(String ip) {
        IpAggregateBucket bucket = aggregateBuckets.get(ip);
        if (bucket == null) {
            return;
        }
        
        List<TrafficAggregateEntry> entries = bucket.flushAndClear();
        if (entries.isEmpty()) {
            return;
        }
        
        for (TrafficAggregateEntry entry : entries) {
            pushAggregateEntry(ip, entry);
        }
        
        if (bucket.getTotalCount() == 0) {
            aggregateBuckets.remove(ip);
        }
    }

    private void pushAggregateEntry(String ip, TrafficAggregateEntry entry) {
        if (degradationHandler.isInDegradationMode()) {
            logger.warn("系统降级，跳过聚合推送: ip={}, uri={}", ip, entry.getUriPattern());
            return;
        }
        
        if (!congestionDetector.canPushImmediately()) {
            logger.debug("网络拥塞，延迟推送: ip={}, uri={}", ip, entry.getUriPattern());
            return;
        }
        
        congestionDetector.recordPushStart();
        
        aggregateExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                TrafficAggregatePushDTO dto = convertToDTO(ip, entry);
                trafficClient.pushAggregateTraffic(dto);
                
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushSuccess(rtt);
                
                logger.info("聚合推送成功: ip={}, uri={}, count={}, isAggregated=true", 
                    ip, entry.getUriPattern(), entry.getCount());
            } catch (Exception e) {
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushFailure(rtt, e);
                
                logger.error("聚合推送失败: ip={}, uri={}, error={}", 
                    ip, entry.getUriPattern(), e.getMessage());
            }
        });
    }

    private TrafficAggregatePushDTO convertToDTO(String ip, TrafficAggregateEntry entry) {
        TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO();
        dto.setIp(ip);
        dto.setState(entry.getState());
        dto.setStateName(IpAttackStateConstant.getStateNameZh(entry.getState()));
        dto.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatTime(new Date()));
        dto.setTotalRequests(entry.getCount());
        dto.setErrorRequests(entry.getErrorCount());
        dto.setConfidence(entry.getConfidence());
        
        if (entry.getEventId() != null && !entry.getEventId().isEmpty()) {
            dto.setEventId(entry.getEventId());
        }
        
        List<UriGroupStats> uriGroups = new ArrayList<>();
        UriGroupStats uriGroup = new UriGroupStats();
        uriGroup.setUriPattern(entry.getUriPattern());
        uriGroup.setHttpMethod(entry.getHttpMethod());
        uriGroup.setCount(entry.getCount());
        uriGroup.setErrorCount(entry.getErrorCount());
        uriGroup.setAvgProcessingTime(entry.getAvgProcessingTime());
        uriGroups.add(uriGroup);
        dto.setUriGroups(uriGroups);
        
        if (entry.getFirstSample() != null) {
            dto.setSamples(Collections.singletonList(
                new TrafficSampleDTO(entry.getFirstSample())
            ));
        }
        
        return dto;
    }

    private String formatTime(Date date) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private void flushAll() {
        for (String ip : aggregateBuckets.keySet()) {
            flushBucket(ip);
        }
    }

    public String getStatistics() {
        int totalEntries = aggregateBuckets.values().stream()
            .mapToInt(IpAggregateBucket::getTotalCount)
            .sum();
        return String.format("TrafficAggregateService{buckets=%d, totalEntries=%d, interval=%dms}", 
            aggregateBuckets.size(), totalEntries, aggregateIntervalMs);
    }

    private static class IpAggregateBucket {
        private final long aggregateIntervalMs;
        private final ConcurrentHashMap<String, TrafficAggregateEntry> entries = new ConcurrentHashMap<>();
        private volatile long lastFlushTime;
        
        public IpAggregateBucket(String ip, long aggregateIntervalMs) {
            this.aggregateIntervalMs = aggregateIntervalMs;
            this.lastFlushTime = System.currentTimeMillis();
        }
        
        public void addSample(TrafficSample sample) {
            String key = generateKey(sample);
            TrafficAggregateEntry entry = entries.computeIfAbsent(key, 
                k -> new TrafficAggregateEntry(sample));
            entry.increment(sample);
        }
        
        private String generateKey(TrafficSample sample) {
            return sample.getRequestUri() + "|" + sample.getHttpMethod() + "|" + sample.getState();
        }
        
        public boolean shouldFlush(long now) {
            return (now - lastFlushTime) >= aggregateIntervalMs && getTotalCount() > 0;
        }
        
        public List<TrafficAggregateEntry> flushAndClear() {
            List<TrafficAggregateEntry> result = new ArrayList<>(entries.values());
            entries.clear();
            lastFlushTime = System.currentTimeMillis();
            return result;
        }
        
        public int getTotalCount() {
            return entries.values().stream()
                .mapToInt(TrafficAggregateEntry::getCount)
                .sum();
        }
    }

    private static class TrafficAggregateEntry {
        private final String uriPattern;
        private final String httpMethod;
        private final int state;
        private final TrafficSample firstSample;
        private final String eventId;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private volatile int confidence;
        
        public TrafficAggregateEntry(TrafficSample sample) {
            this.uriPattern = sample.getRequestUri();
            this.httpMethod = sample.getHttpMethod();
            this.state = sample.getState();
            this.firstSample = sample;
            this.eventId = sample.getEventId();
            this.confidence = sample.getConfidence();
        }
        
        public void increment(TrafficSample sample) {
            count.incrementAndGet();
            if (sample.isError()) {
                errorCount.incrementAndGet();
            }
            totalProcessingTime.addAndGet(sample.getProcessingTime());
            confidence = sample.getConfidence();
        }
        
        public String getUriPattern() { return uriPattern; }
        public String getHttpMethod() { return httpMethod; }
        public int getState() { return state; }
        public TrafficSample getFirstSample() { return firstSample; }
        public String getEventId() { return eventId; }
        public int getCount() { return count.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public int getConfidence() { return confidence; }
        public long getAvgProcessingTime() {
            int c = count.get();
            return c > 0 ? totalProcessingTime.get() / c : 0;
        }
    }
}
