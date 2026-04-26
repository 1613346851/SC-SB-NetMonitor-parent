package com.network.gateway.traffic;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final ConcurrentHashMap<String, IpStateBuckets> ipStateBuckets = new ConcurrentHashMap<>();
    
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

    private final GatewayConfigCache configCache;
    private final IpAttackStateCache attackStateCache;
    private final MonitorServiceTrafficClient trafficClient;
    private final PushDegradationHandler degradationHandler;
    private final NetworkCongestionDetector congestionDetector;

    private long aggregateIntervalMs = 5000;

    public TrafficAggregateService(GatewayConfigCache configCache,
                                   IpAttackStateCache attackStateCache,
                                   MonitorServiceTrafficClient trafficClient,
                                   PushDegradationHandler degradationHandler,
                                   NetworkCongestionDetector congestionDetector) {
        this.configCache = configCache;
        this.attackStateCache = attackStateCache;
        this.trafficClient = trafficClient;
        this.degradationHandler = degradationHandler;
        this.congestionDetector = congestionDetector;
    }

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
        
        int state = sample.getState();
        IpStateBuckets buckets = getOrCreateBuckets(ip);
        StateAggregateBucket bucket = buckets.getOrCreateBucket(state);
        bucket.addSample(sample);
        
        logger.debug("流量加入聚合桶: ip={}, state={}, uri={}, method={}, 当前聚合数={}", 
            ip, IpAttackStateConstant.getStateNameZh(state), 
            sample.getRequestUri(), sample.getHttpMethod(), bucket.getTotalCount());
    }

    public void onStateTransition(String ip, int fromState, int toState) {
        if (isShutdown.get()) {
            return;
        }
        
        IpStateBuckets buckets = ipStateBuckets.get(ip);
        if (buckets == null) {
            return;
        }
        
        StateAggregateBucket fromBucket = buckets.getBucket(fromState);
        if (fromBucket != null && fromBucket.getTotalCount() > 0) {
            logger.info("状态转换触发聚合推送: ip={}, {} -> {}, 前一状态流量数={}", 
                ip, IpAttackStateConstant.getStateNameZh(fromState), 
                IpAttackStateConstant.getStateNameZh(toState), fromBucket.getTotalCount());
            
            flushStateBucket(ip, fromState);
        }
    }

    private IpStateBuckets getOrCreateBuckets(String ip) {
        return ipStateBuckets.computeIfAbsent(ip, k -> new IpStateBuckets(ip, aggregateIntervalMs));
    }

    private void flushExpiredBuckets() {
        long now = System.currentTimeMillis();
        List<FlushTask> toFlush = new ArrayList<>();
        
        for (Map.Entry<String, IpStateBuckets> entry : ipStateBuckets.entrySet()) {
            String ip = entry.getKey();
            IpStateBuckets buckets = entry.getValue();
            
            for (Map.Entry<Integer, StateAggregateBucket> stateEntry : buckets.getAllBuckets().entrySet()) {
                int state = stateEntry.getKey();
                StateAggregateBucket bucket = stateEntry.getValue();
                
                if (bucket.shouldFlush(now)) {
                    toFlush.add(new FlushTask(ip, state, bucket));
                }
            }
        }
        
        for (FlushTask task : toFlush) {
            flushStateBucket(task.ip, task.state);
        }
        
        if (!toFlush.isEmpty()) {
            logger.info("周期性刷新: 刷新了{}个状态桶", toFlush.size());
        }
    }

    private void flushStateBucket(String ip, int state) {
        IpStateBuckets buckets = ipStateBuckets.get(ip);
        if (buckets == null) {
            return;
        }
        
        StateAggregateBucket bucket = buckets.getBucket(state);
        if (bucket == null) {
            return;
        }
        
        List<TrafficAggregateEntry> entries = bucket.flushAndClear();
        if (entries.isEmpty()) {
            return;
        }
        
        for (TrafficAggregateEntry entry : entries) {
            pushAggregateEntry(ip, state, entry);
        }
        
        if (bucket.getTotalCount() == 0) {
            buckets.removeBucket(state);
        }
        
        if (buckets.isEmpty()) {
            ipStateBuckets.remove(ip);
        }
    }

    private void pushAggregateEntry(String ip, int state, TrafficAggregateEntry entry) {
        if (degradationHandler.isInDegradationMode()) {
            logger.warn("系统降级，跳过聚合推送: ip={}, state={}, uri={}", 
                ip, IpAttackStateConstant.getStateNameZh(state), entry.getUriPattern());
            return;
        }
        
        if (!congestionDetector.canPushImmediately()) {
            logger.debug("网络拥塞，延迟推送: ip={}, state={}, uri={}", 
                ip, IpAttackStateConstant.getStateNameZh(state), entry.getUriPattern());
            return;
        }
        
        congestionDetector.recordPushStart();
        
        final int confidence = attackStateCache.getConfidence(ip);
        
        aggregateExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                TrafficAggregatePushDTO dto = convertToDTO(ip, state, entry, confidence);
                trafficClient.pushAggregateTraffic(dto);
                
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushSuccess(rtt);
                
                logger.info("聚合推送成功: ip={}, state={}, uri={}, count={}, confidence={}%, isAggregated=true", 
                    ip, IpAttackStateConstant.getStateNameZh(state), entry.getUriPattern(), entry.getCount(), confidence);
            } catch (Exception e) {
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushFailure(rtt, e);
                
                logger.error("聚合推送失败: ip={}, state={}, uri={}, error={}", 
                    ip, IpAttackStateConstant.getStateNameZh(state), entry.getUriPattern(), e.getMessage());
            }
        });
    }

    private TrafficAggregatePushDTO convertToDTO(String ip, int state, TrafficAggregateEntry entry, int confidence) {
        TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO();
        dto.setIp(ip);
        dto.setState(state);
        dto.setStateName(IpAttackStateConstant.getStateNameZh(state));
        dto.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatTime(new Date()));
        dto.setTotalRequests(entry.getCount());
        dto.setErrorRequests(entry.getErrorCount());
        dto.setConfidence(confidence);
        
        if (entry.getEventId() != null && !entry.getEventId().isEmpty()) {
            dto.setEventId(entry.getEventId());
        }
        
        dto.setStartTime(formatTime(new Date(entry.getFirstTimestamp())));
        dto.setEndTime(formatTime(new Date(entry.getLastTimestamp())));
        
        List<UriGroupStats> uriGroups = new ArrayList<>();
        UriGroupStats uriGroup = new UriGroupStats();
        uriGroup.setUriPattern(entry.getUriPattern());
        uriGroup.setHttpMethod(entry.getHttpMethod());
        uriGroup.setResponseStatus(entry.getResponseStatus());
        uriGroup.setTargetIp(entry.getTargetIp());
        uriGroup.setTargetPort(entry.getTargetPort());
        uriGroup.setProtocol(entry.getProtocol());
        uriGroup.setUserAgent(entry.getUserAgent());
        uriGroup.setSourcePort(entry.getSourcePort());
        uriGroup.setHeaders(entry.getHeaders());
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
        for (String ip : ipStateBuckets.keySet()) {
            IpStateBuckets buckets = ipStateBuckets.get(ip);
            if (buckets != null) {
                for (Integer state : new ArrayList<>(buckets.getAllBuckets().keySet())) {
                    flushStateBucket(ip, state);
                }
            }
        }
    }

    public String getStatistics() {
        int totalBuckets = 0;
        int totalEntries = 0;
        
        for (IpStateBuckets buckets : ipStateBuckets.values()) {
            for (StateAggregateBucket bucket : buckets.getAllBuckets().values()) {
                totalBuckets++;
                totalEntries += bucket.getTotalCount();
            }
        }
        
        return String.format("TrafficAggregateService{ipBuckets=%d, stateBuckets=%d, totalEntries=%d, interval=%dms}", 
            ipStateBuckets.size(), totalBuckets, totalEntries, aggregateIntervalMs);
    }

    private static class FlushTask {
        final String ip;
        final int state;
        final StateAggregateBucket bucket;
        
        FlushTask(String ip, int state, StateAggregateBucket bucket) {
            this.ip = ip;
            this.state = state;
            this.bucket = bucket;
        }
    }

    private static class IpStateBuckets {
        private final String ip;
        private final long aggregateIntervalMs;
        private final ConcurrentHashMap<Integer, StateAggregateBucket> stateBuckets = new ConcurrentHashMap<>();
        
        public IpStateBuckets(String ip, long aggregateIntervalMs) {
            this.ip = ip;
            this.aggregateIntervalMs = aggregateIntervalMs;
        }
        
        public StateAggregateBucket getOrCreateBucket(int state) {
            return stateBuckets.computeIfAbsent(state, 
                s -> new StateAggregateBucket(ip, s, aggregateIntervalMs));
        }
        
        public StateAggregateBucket getBucket(int state) {
            return stateBuckets.get(state);
        }
        
        public void removeBucket(int state) {
            stateBuckets.remove(state);
        }
        
        public Map<Integer, StateAggregateBucket> getAllBuckets() {
            return new HashMap<>(stateBuckets);
        }
        
        public boolean isEmpty() {
            return stateBuckets.isEmpty();
        }
    }

    private static class StateAggregateBucket {
        private final String ip;
        private final int state;
        private final long aggregateIntervalMs;
        private volatile long lastFlushTime;
        private final ConcurrentHashMap<String, TrafficAggregateEntry> entries = new ConcurrentHashMap<>();
        
        public StateAggregateBucket(String ip, int state, long aggregateIntervalMs) {
            this.ip = ip;
            this.state = state;
            this.aggregateIntervalMs = aggregateIntervalMs;
            this.lastFlushTime = System.currentTimeMillis();
        }
        
        public void addSample(TrafficSample sample) {
            String key = sample.generateAggregateKey();
            TrafficAggregateEntry entry = entries.computeIfAbsent(key, 
                k -> new TrafficAggregateEntry(sample));
            entry.increment(sample);
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
        private final int responseStatus;
        private final String targetIp;
        private final Integer targetPort;
        private final String protocol;
        private final String userAgent;
        private final TrafficSample firstSample;
        private final String eventId;
        private final Integer sourcePort;
        private final Map<String, String> headers;
        private final long firstTimestamp;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final AtomicLong lastTimestamp = new AtomicLong(0);
        
        public TrafficAggregateEntry(TrafficSample sample) {
            this.uriPattern = sample.getRequestUri();
            this.httpMethod = sample.getHttpMethod();
            this.responseStatus = sample.getResponseStatus();
            this.targetIp = sample.getTargetIp();
            this.targetPort = sample.getTargetPort();
            this.protocol = sample.getProtocol();
            this.userAgent = sample.getUserAgent();
            this.firstSample = sample;
            this.eventId = sample.getEventId();
            this.sourcePort = sample.getSourcePort();
            this.headers = sample.getHeaders();
            this.firstTimestamp = sample.getTimestamp();
            this.lastTimestamp.set(sample.getTimestamp());
        }
        
        public void increment(TrafficSample sample) {
            count.incrementAndGet();
            if (sample.isError()) {
                errorCount.incrementAndGet();
            }
            totalProcessingTime.addAndGet(sample.getProcessingTime());
            lastTimestamp.set(sample.getTimestamp());
        }
        
        public String getUriPattern() { return uriPattern; }
        public String getHttpMethod() { return httpMethod; }
        public int getResponseStatus() { return responseStatus; }
        public String getTargetIp() { return targetIp; }
        public Integer getTargetPort() { return targetPort; }
        public String getProtocol() { return protocol; }
        public String getUserAgent() { return userAgent; }
        public TrafficSample getFirstSample() { return firstSample; }
        public String getEventId() { return eventId; }
        public Integer getSourcePort() { return sourcePort; }
        public Map<String, String> getHeaders() { return headers; }
        public long getFirstTimestamp() { return firstTimestamp; }
        public long getLastTimestamp() { return lastTimestamp.get(); }
        public int getCount() { return count.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public long getAvgProcessingTime() {
            int c = count.get();
            return c > 0 ? totalProcessingTime.get() / c : 0;
        }
    }
}
