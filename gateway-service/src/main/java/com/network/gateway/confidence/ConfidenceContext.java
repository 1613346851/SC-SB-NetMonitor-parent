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

    public ConfidenceContext() {
    }

    public ConfidenceContext(String ip) {
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
}
