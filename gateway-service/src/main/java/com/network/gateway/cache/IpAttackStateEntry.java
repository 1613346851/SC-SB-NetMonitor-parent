package com.network.gateway.cache;

import com.network.gateway.constant.IpAttackStateConstant;
import lombok.Data;

import java.io.Serializable;

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
    }

    public IpAttackStateEntry(String ip) {
        this();
        this.ip = ip;
        this.firstRequestTime = System.currentTimeMillis();
        this.lastRequestTime = this.firstRequestTime;
    }

    public void incrementRequestCount() {
        this.requestCount++;
        this.stateRequestCount++;
        this.lastRequestTime = System.currentTimeMillis();
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
        return (System.currentTimeMillis() - stateUpdateTime) < IpAttackStateConstant.COOLDOWN_DURATION_MS;
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
}
