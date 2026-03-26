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

    public ConfidenceResult() {
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
        return String.format("ConfidenceResult{ip=%s, raw=%d, smoothed=%d, total=%d}",
            ip, rawConfidence, smoothedConfidence, breakdown != null ? breakdown.getTotalScore() : 0);
    }
}
