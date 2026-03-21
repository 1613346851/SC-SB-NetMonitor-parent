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
    private int attackCount;

    public IpAttackStateEntry() {
        this.state = IpAttackStateConstant.NORMAL;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
        this.requestCount = 0;
        this.rateLimitCount = 0;
        this.attackCount = 0;
    }

    public IpAttackStateEntry(String ip) {
        this();
        this.ip = ip;
        this.firstRequestTime = System.currentTimeMillis();
        this.lastRequestTime = this.firstRequestTime;
    }

    public void incrementRequestCount() {
        this.requestCount++;
        this.lastRequestTime = System.currentTimeMillis();
    }

    public void incrementRateLimitCount() {
        this.rateLimitCount++;
        this.lastRequestTime = System.currentTimeMillis();
    }

    public void incrementAttackCount() {
        this.attackCount++;
        this.lastRequestTime = System.currentTimeMillis();
    }

    public void updateState(int newState) {
        this.state = newState;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
    }

    public void updateState(int newState, String eventId) {
        this.state = newState;
        this.eventId = eventId;
        this.stateUpdateTime = System.currentTimeMillis();
        this.stateExpireTime = this.stateUpdateTime + IpAttackStateConstant.STATE_EXPIRE_MS;
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
        return state == IpAttackStateConstant.DEFENDED;
    }

    public boolean shouldSkipDefenseAction() {
        return state == IpAttackStateConstant.DEFENDED || state == IpAttackStateConstant.COOLDOWN;
    }
}
