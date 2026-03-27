package com.network.gateway.confidence;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
public class ConfidenceContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int currentState;
    private int currentRps;
    private int thresholdRps;
    private long durationMs;
    private int requestCount;
    private int uniqueUriCount;
    private Set<String> uniqueUris;
    private boolean matchedAttackPattern;
    private String attackPatternName;
    private boolean hasAttackHistory;
    private int normalRequestCount;
    private int attackHistoryCount;
    private long stateDurationMs;
    private int stateRequestCount;
    private int slidingWindowRps;
    private int errorRate;
    private int blockedRate;
    private boolean isSlowAttack;
    private boolean isDistributedAttack;
    private boolean isPeakPeriod;
    private int peakAdaptationFactor;
    private String traceId;

    public ConfidenceContext() {
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public ConfidenceContext(String ip) {
        this();
        this.ip = ip;
    }

    public double getExceedRatio() {
        if (thresholdRps <= 0) {
            return 0;
        }
        return (double) currentRps / thresholdRps;
    }

    public double getDurationSeconds() {
        return durationMs / 1000.0;
    }

    public double getStateDurationSeconds() {
        return stateDurationMs / 1000.0;
    }

    public boolean isHighFrequency() {
        return getExceedRatio() > 2.0;
    }

    public boolean isDiverseAttack() {
        return uniqueUriCount >= 3;
    }

    public boolean isPersistentAttack() {
        return durationMs >= 10000;
    }

    public boolean hasNormalHistory() {
        return normalRequestCount > 100;
    }

    public boolean isSlowAttackPattern() {
        return isSlowAttack || (slidingWindowRps > 0 && slidingWindowRps < thresholdRps * 0.5 && durationMs > 60000);
    }

    public boolean isDistributedAttackPattern() {
        return isDistributedAttack || (uniqueUriCount > 10 && errorRate < 20);
    }

    public boolean isInPeakPeriod() {
        return isPeakPeriod;
    }
}
