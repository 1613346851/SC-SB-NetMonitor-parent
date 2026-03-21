package com.network.monitor.cache;

import com.network.monitor.common.constant.IpAttackStateConstant;
import com.network.monitor.common.util.IpNormalizeUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
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

    public boolean hasActiveEvent() {
        return eventId != null && !eventId.isEmpty() && !isStateExpired();
    }
}
