package com.network.gateway.cache;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class TrafficAggregate {

    private String sourceIp;
    private String requestUri;
    private String httpMethod;
    private String userAgent;
    private String contentType;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private long totalProcessingTime = 0;
    private String stateTag;

    public TrafficAggregate() {
        this.startTime = LocalDateTime.now();
        this.lastUpdateTime = this.startTime;
    }

    public int increment() {
        this.lastUpdateTime = LocalDateTime.now();
        return count.incrementAndGet();
    }

    public void incrementError() {
        this.lastUpdateTime = LocalDateTime.now();
        errorCount.incrementAndGet();
    }

    public void addProcessingTime(long processingTime) {
        this.totalProcessingTime += processingTime;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public int getCount() {
        return count.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public long getAverageProcessingTime() {
        int c = count.get();
        return c > 0 ? totalProcessingTime / c : 0;
    }

    public boolean isExpired(long expireMs) {
        if (lastUpdateTime == null) {
            return true;
        }
        return java.time.Duration.between(lastUpdateTime, LocalDateTime.now()).toMillis() > expireMs;
    }

    public void reset() {
        count.set(0);
        errorCount.set(0);
        totalProcessingTime = 0;
        startTime = LocalDateTime.now();
        lastUpdateTime = startTime;
    }
}
