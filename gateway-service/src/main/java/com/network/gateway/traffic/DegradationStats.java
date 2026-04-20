package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class DegradationStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean degradationMode;
    private int localCacheSize;
    private int consecutiveFailures;
    private int totalDegradedCount;
    private int recoveredCount;

    public DegradationStats() {
    }

    @Override
    public String toString() {
        return String.format("DegradationStats{mode=%s, cacheSize=%d, failures=%d, degraded=%d, recovered=%d}",
            degradationMode ? "DEGRADED" : "NORMAL",
            localCacheSize,
            consecutiveFailures,
            totalDegradedCount,
            recoveredCount);
    }
}
