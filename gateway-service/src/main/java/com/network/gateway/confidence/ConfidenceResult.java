package com.network.gateway.confidence;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ConfidenceResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int rawConfidence;
    private int smoothedConfidence;
    private ScoreBreakdown breakdown;
    private long timestamp;
    private String reason;
    private String traceId;

    public ConfidenceResult() {
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public int getDelta() {
        if (smoothedConfidence > 0 && rawConfidence > 0) {
            return smoothedConfidence - rawConfidence;
        }
        return 0;
    }

    public Map<String, Integer> getScoreBreakdown() {
        if (breakdown != null) {
            return breakdown.toMap();
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ConfidenceResult{ip=%s, raw=%d, smoothed=%d, total=%d, traceId=%s}",
            ip, rawConfidence, smoothedConfidence, breakdown != null ? breakdown.getTotalScore() : 0, traceId);
    }
}
