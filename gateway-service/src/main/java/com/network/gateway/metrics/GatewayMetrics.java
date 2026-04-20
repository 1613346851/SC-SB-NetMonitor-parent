package com.network.gateway.metrics;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GatewayMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> stateCounters = new ConcurrentHashMap<>();
    
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalBlocked = new AtomicLong(0);
    private final AtomicLong totalRateLimited = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    private final AtomicInteger normalStateCount = new AtomicInteger(0);
    private final AtomicInteger suspiciousStateCount = new AtomicInteger(0);
    private final AtomicInteger attackingStateCount = new AtomicInteger(0);
    private final AtomicInteger defendedStateCount = new AtomicInteger(0);
    private final AtomicInteger cooldownStateCount = new AtomicInteger(0);
    
    private final AtomicLong stateTransitions = new AtomicLong(0);
    private final AtomicLong trafficPushSuccess = new AtomicLong(0);
    private final AtomicLong trafficPushFailure = new AtomicLong(0);
    private final AtomicLong defenseLogPushSuccess = new AtomicLong(0);
    private final AtomicLong defenseLogPushFailure = new AtomicLong(0);
    
    private long startTime = System.currentTimeMillis();

    public void incrementTotalRequests() {
        totalRequests.incrementAndGet();
    }

    public void incrementBlocked() {
        totalBlocked.incrementAndGet();
    }

    public void incrementRateLimited() {
        totalRateLimited.incrementAndGet();
    }

    public void incrementErrors() {
        totalErrors.incrementAndGet();
    }

    public void incrementStateTransitions() {
        stateTransitions.incrementAndGet();
    }

    public void incrementTrafficPushSuccess() {
        trafficPushSuccess.incrementAndGet();
    }

    public void incrementTrafficPushFailure() {
        trafficPushFailure.incrementAndGet();
    }

    public void incrementDefenseLogPushSuccess() {
        defenseLogPushSuccess.incrementAndGet();
    }

    public void incrementDefenseLogPushFailure() {
        defenseLogPushFailure.incrementAndGet();
    }

    public void updateStateCount(int normal, int suspicious, int attacking, int defended, int cooldown) {
        normalStateCount.set(normal);
        suspiciousStateCount.set(suspicious);
        attackingStateCount.set(attacking);
        defendedStateCount.set(defended);
        cooldownStateCount.set(cooldown);
    }

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }

    public long getCounter(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    public long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    public MetricsSnapshot getSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setTimestamp(System.currentTimeMillis());
        snapshot.setUptime(System.currentTimeMillis() - startTime);
        
        snapshot.setTotalRequests(totalRequests.get());
        snapshot.setTotalBlocked(totalBlocked.get());
        snapshot.setTotalRateLimited(totalRateLimited.get());
        snapshot.setTotalErrors(totalErrors.get());
        
        snapshot.setNormalStateCount(normalStateCount.get());
        snapshot.setSuspiciousStateCount(suspiciousStateCount.get());
        snapshot.setAttackingStateCount(attackingStateCount.get());
        snapshot.setDefendedStateCount(defendedStateCount.get());
        snapshot.setCooldownStateCount(cooldownStateCount.get());
        
        snapshot.setStateTransitions(stateTransitions.get());
        snapshot.setTrafficPushSuccess(trafficPushSuccess.get());
        snapshot.setTrafficPushFailure(trafficPushFailure.get());
        snapshot.setDefenseLogPushSuccess(defenseLogPushSuccess.get());
        snapshot.setDefenseLogPushFailure(defenseLogPushFailure.get());
        
        double blockRate = totalRequests.get() > 0 
            ? (double) totalBlocked.get() / totalRequests.get() * 100 : 0;
        snapshot.setBlockRate(blockRate);
        
        double errorRate = totalRequests.get() > 0 
            ? (double) totalErrors.get() / totalRequests.get() * 100 : 0;
        snapshot.setErrorRate(errorRate);
        
        return snapshot;
    }

    public void reset() {
        totalRequests.set(0);
        totalBlocked.set(0);
        totalRateLimited.set(0);
        totalErrors.set(0);
        stateTransitions.set(0);
        trafficPushSuccess.set(0);
        trafficPushFailure.set(0);
        defenseLogPushSuccess.set(0);
        defenseLogPushFailure.set(0);
        counters.clear();
        gauges.clear();
    }

    public String getSummary() {
        return String.format(
            "GatewayMetrics{requests=%d, blocked=%d, rateLimited=%d, errors=%d, " +
            "states=[normal=%d, suspicious=%d, attacking=%d, defended=%d, cooldown=%d], " +
            "transitions=%d, trafficPush=[success=%d, failure=%d], defensePush=[success=%d, failure=%d]}",
            totalRequests.get(), totalBlocked.get(), totalRateLimited.get(), totalErrors.get(),
            normalStateCount.get(), suspiciousStateCount.get(), attackingStateCount.get(),
            defendedStateCount.get(), cooldownStateCount.get(),
            stateTransitions.get(), trafficPushSuccess.get(), trafficPushFailure.get(),
            defenseLogPushSuccess.get(), defenseLogPushFailure.get()
        );
    }

    @Data
    public static class MetricsSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long timestamp;
        private long uptime;
        private long totalRequests;
        private long totalBlocked;
        private long totalRateLimited;
        private long totalErrors;
        private int normalStateCount;
        private int suspiciousStateCount;
        private int attackingStateCount;
        private int defendedStateCount;
        private int cooldownStateCount;
        private long stateTransitions;
        private long trafficPushSuccess;
        private long trafficPushFailure;
        private long defenseLogPushSuccess;
        private long defenseLogPushFailure;
        private double blockRate;
        private double errorRate;
    }
}
