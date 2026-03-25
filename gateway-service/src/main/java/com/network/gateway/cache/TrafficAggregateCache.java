package com.network.gateway.cache;

import com.network.gateway.dto.TrafficMonitorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TrafficAggregateCache {

    private static final Logger logger = LoggerFactory.getLogger(TrafficAggregateCache.class);

    private final ConcurrentHashMap<String, TrafficAggregate> aggregateMap = new ConcurrentHashMap<>();
    
    private static final long DEFAULT_EXPIRE_MS = 60000;

    public TrafficAggregate getOrAdd(TrafficMonitorDTO traffic, String stateTag) {
        String aggregateKey = buildAggregateKey(traffic);
        
        return aggregateMap.compute(aggregateKey, (key, existing) -> {
            if (existing == null || existing.isExpired(DEFAULT_EXPIRE_MS)) {
                TrafficAggregate aggregate = new TrafficAggregate();
                aggregate.setSourceIp(traffic.getSourceIp());
                aggregate.setRequestUri(traffic.getRequestUri());
                aggregate.setHttpMethod(traffic.getHttpMethod());
                aggregate.setUserAgent(truncateUserAgent(traffic.getUserAgent()));
                aggregate.setContentType(traffic.getContentType());
                aggregate.setStartTime(LocalDateTime.now());
                aggregate.setStateTag(stateTag);
                logger.debug("创建新的流量聚合条目: key={}", key);
                return aggregate;
            }
            return existing;
        });
    }

    public String buildAggregateKey(TrafficMonitorDTO traffic) {
        String userAgentHash = traffic.getUserAgent() != null ? 
            String.valueOf(traffic.getUserAgent().hashCode()) : "";
        
        return String.format("%s|%s|%s|%s",
            traffic.getSourceIp(),
            traffic.getRequestUri(),
            traffic.getHttpMethod(),
            userAgentHash
        );
    }

    public void incrementCount(TrafficMonitorDTO traffic, String stateTag, long processingTime, boolean isError) {
        TrafficAggregate aggregate = getOrAdd(traffic, stateTag);
        aggregate.increment();
        aggregate.addProcessingTime(processingTime);
        if (isError) {
            aggregate.incrementError();
        }
    }

    public List<TrafficAggregate> flushAll() {
        List<TrafficAggregate> result = new ArrayList<>();
        
        for (Map.Entry<String, TrafficAggregate> entry : aggregateMap.entrySet()) {
            TrafficAggregate aggregate = entry.getValue();
            if (aggregate.getCount() > 0) {
                result.add(aggregate);
            }
        }
        
        aggregateMap.clear();
        logger.info("刷新流量聚合缓存: 共{}条聚合记录", result.size());
        return result;
    }

    public List<TrafficAggregate> flushExpired() {
        List<TrafficAggregate> result = new ArrayList<>();
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, TrafficAggregate> entry : aggregateMap.entrySet()) {
            TrafficAggregate aggregate = entry.getValue();
            if (aggregate.isExpired(DEFAULT_EXPIRE_MS) && aggregate.getCount() > 0) {
                result.add(aggregate);
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            aggregateMap.remove(key);
        }
        
        if (!result.isEmpty()) {
            logger.debug("刷新过期流量聚合缓存: 共{}条聚合记录", result.size());
        }
        return result;
    }

    public int getSize() {
        return aggregateMap.size();
    }

    public long getTotalCount() {
        return aggregateMap.values().stream()
            .mapToLong(TrafficAggregate::getCount)
            .sum();
    }

    public void cleanup() {
        int removed = 0;
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, TrafficAggregate> entry : aggregateMap.entrySet()) {
            if (entry.getValue().isExpired(DEFAULT_EXPIRE_MS)) {
                toRemove.add(entry.getKey());
                removed++;
            }
        }
        
        for (String key : toRemove) {
            aggregateMap.remove(key);
        }
        
        if (removed > 0) {
            logger.debug("清理过期流量聚合缓存: 移除{}条", removed);
        }
    }

    public String getStats() {
        return String.format("聚合条目数:%d 总请求数:%d", getSize(), getTotalCount());
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
    }
}
