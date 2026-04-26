package com.network.gateway.cache;

import com.network.gateway.constant.IpAttackStateConstant;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IpAttackStateEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int state;
    private long stateUpdateTime;
    private long stateExpireTime;
    private String eventId;
    private int requestCount;
    private long firstRequestTime;
    private long lastRequestTime;
    private int rateLimitCount;
    private long rateLimitWindowStart;
    private int attackCount;

    private int stateRequestCount;
    private long stateStartTime;
    private int previousState;

    private long lastPushTime;
    private long periodStartTime;
    private Map<String, RequestAggregate> currentAggregates;

    private long cooldownDuration;
    private long cooldownEndTime;
    private int attackHistoryCount;
    private int confidence;
    private Set<String> uniqueUris;
    private long attackStartTime;
    private int attackRequestCount;
    private String transitionReason;
    private int peakRps;

    private static final int MAX_SAMPLE_SIZE = 5;

    public IpAttackStateEntry() {
        this.state = IpAttackStateConstant.NORMAL;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
        this.requestCount = 0;
        this.rateLimitCount = 0;
        this.rateLimitWindowStart = System.currentTimeMillis();
        this.attackCount = 0;
        this.stateRequestCount = 0;
        this.stateStartTime = System.currentTimeMillis();
        this.previousState = IpAttackStateConstant.NORMAL;
        this.lastPushTime = System.currentTimeMillis();
        this.periodStartTime = System.currentTimeMillis();
        this.currentAggregates = new ConcurrentHashMap<>();
        this.cooldownDuration = IpAttackStateConstant.COOLDOWN_DURATION_MS;
        this.attackHistoryCount = 0;
        this.confidence = 0;
        this.uniqueUris = new HashSet<>();
        this.attackRequestCount = 0;
    }

    public IpAttackStateEntry(String ip) {
        this();
        this.ip = ip;
        this.firstRequestTime = System.currentTimeMillis();
        this.lastRequestTime = this.firstRequestTime;
    }

    public void addAggregateRequest(String aggregateKey, com.network.gateway.dto.TrafficMonitorDTO traffic) {
        RequestAggregate aggregate = currentAggregates.computeIfAbsent(aggregateKey, 
            k -> new RequestAggregate(aggregateKey, MAX_SAMPLE_SIZE));
        aggregate.addRequest(traffic);
        this.lastRequestTime = System.currentTimeMillis();
    }

    public Map<String, RequestAggregate> getAndResetAggregates() {
        Map<String, RequestAggregate> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, RequestAggregate> entry : currentAggregates.entrySet()) {
            result.put(entry.getKey(), entry.getValue().copy());
        }
        currentAggregates.clear();
        this.periodStartTime = System.currentTimeMillis();
        return result;
    }

    public Map<String, RequestAggregate> getCurrentAggregates() {
        return currentAggregates;
    }

    public int getTotalAggregateCount() {
        return currentAggregates.values().stream()
            .mapToInt(RequestAggregate::getCount)
            .sum();
    }

    public int getAggregateGroupCount() {
        return currentAggregates.size();
    }

    public long getPeriodDuration() {
        return System.currentTimeMillis() - this.periodStartTime;
    }

    public boolean shouldPushByPeriod(long periodIntervalMs) {
        return System.currentTimeMillis() - this.lastPushTime >= periodIntervalMs;
    }

    public void updateLastPushTime() {
        this.lastPushTime = System.currentTimeMillis();
    }

    public LocalDateTime getPeriodStartTimeAsDateTime() {
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(periodStartTime),
            java.time.ZoneId.systemDefault()
        );
    }

    public LocalDateTime getPeriodEndTimeAsDateTime() {
        return LocalDateTime.now();
    }

    public void incrementRequestCount() {
        this.requestCount++;
        this.stateRequestCount++;
        this.lastRequestTime = System.currentTimeMillis();
    }

    public void incrementRequestCount(String uri) {
        incrementRequestCount();
        if (uri != null) {
            this.uniqueUris.add(uri);
        }
    }

    public int incrementRateLimitCount() {
        return incrementRateLimitCount(60000);
    }

    public int incrementRateLimitCount(long windowMs) {
        long now = System.currentTimeMillis();
        if (now - rateLimitWindowStart > windowMs) {
            this.rateLimitCount = 1;
            this.rateLimitWindowStart = now;
        } else {
            this.rateLimitCount++;
        }
        this.lastRequestTime = now;
        return this.rateLimitCount;
    }

    public void resetRateLimitCount() {
        this.rateLimitCount = 0;
        this.rateLimitWindowStart = System.currentTimeMillis();
    }

    public void incrementAttackCount() {
        this.attackCount++;
        this.lastRequestTime = System.currentTimeMillis();
    }

    public void updateState(int newState) {
        this.previousState = this.state;
        this.state = newState;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
        this.stateRequestCount = 0;
        this.stateStartTime = System.currentTimeMillis();
    }

    public void updateState(int newState, String eventId) {
        this.previousState = this.state;
        this.state = newState;
        this.eventId = eventId;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
        this.stateRequestCount = 0;
        this.stateStartTime = System.currentTimeMillis();
    }

    public void updateState(int newState, String eventId, String reason) {
        this.previousState = this.state;
        this.state = newState;
        this.eventId = eventId;
        this.transitionReason = reason;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
        this.stateRequestCount = 0;
        this.stateStartTime = System.currentTimeMillis();
    }

    public int getAndResetStateRequestCount() {
        int count = this.stateRequestCount;
        this.stateRequestCount = 0;
        return count;
    }

    public long getStateDuration() {
        return System.currentTimeMillis() - this.stateStartTime;
    }

    public boolean isStateExpired() {
        return System.currentTimeMillis() > stateExpireTime;
    }

    public boolean isInCooldownPeriod() {
        if (state != IpAttackStateConstant.COOLDOWN) {
            return false;
        }
        if (cooldownEndTime > 0) {
            return System.currentTimeMillis() < cooldownEndTime;
        }
        return (System.currentTimeMillis() - stateUpdateTime) < cooldownDuration;
    }

    public long getRemainingCooldownTime() {
        if (state != IpAttackStateConstant.COOLDOWN) {
            return 0;
        }
        if (cooldownEndTime > 0) {
            return Math.max(0, cooldownEndTime - System.currentTimeMillis());
        }
        return Math.max(0, cooldownDuration - (System.currentTimeMillis() - stateUpdateTime));
    }

    public void setDynamicCooldownDuration(long duration) {
        this.cooldownDuration = duration;
        this.cooldownEndTime = System.currentTimeMillis() + duration;
    }

    public void startAttackTracking() {
        this.attackStartTime = System.currentTimeMillis();
        this.attackRequestCount = 0;
        this.uniqueUris.clear();
        this.peakRps = 0;
    }

    public void incrementAttackRequestCount() {
        this.attackRequestCount++;
    }

    public long getAttackDuration() {
        if (attackStartTime > 0) {
            return System.currentTimeMillis() - attackStartTime;
        }
        return 0;
    }

    public int getUniqueUriCount() {
        return uniqueUris.size();
    }

    public Set<String> getUniqueUris() {
        return uniqueUris;
    }

    public boolean shouldSkipTrafficPush() {
        return false;
    }

    public boolean shouldSkipDefenseAction() {
        return state == IpAttackStateConstant.DEFENDED || state == IpAttackStateConstant.COOLDOWN;
    }

    public boolean isInAttackState() {
        return state == IpAttackStateConstant.ATTACKING || state == IpAttackStateConstant.DEFENDED;
    }

    public AttackContext toAttackContext() {
        AttackContext context = new AttackContext();
        context.setIp(this.ip);
        context.setConfidence(this.confidence);
        context.setDuration(getAttackDuration());
        context.setRequestCount(this.attackRequestCount);
        context.setUniqueUriCount(getUniqueUriCount());
        context.setAttackHistoryCount(this.attackHistoryCount);
        return context;
    }
}
