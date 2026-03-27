package com.network.gateway.confidence;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class ScoreBreakdown implements Serializable {

    private static final long serialVersionUID = 1L;

    private int baseScore = 0;
    private int frequencyScore = 0;
    private int diversityScore = 0;
    private int persistenceScore = 0;
    private int patternScore = 0;
    private int normalBehaviorDeduction = 0;
    private int slowAttackScore = 0;
    private int distributedAttackScore = 0;
    private int peakAdaptationScore = 0;
    private int stateDurationScore = 0;

    public ScoreBreakdown() {
    }

    public int getTotalScore() {
        int total = baseScore + frequencyScore + diversityScore + persistenceScore + patternScore 
                  + slowAttackScore + distributedAttackScore + stateDurationScore
                  - normalBehaviorDeduction + peakAdaptationScore;
        int minScore = 10;
        total = Math.max(total, minScore);
        return Math.min(100, Math.max(0, total));
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("baseScore", baseScore);
        map.put("frequencyScore", frequencyScore);
        map.put("diversityScore", diversityScore);
        map.put("persistenceScore", persistenceScore);
        map.put("patternScore", patternScore);
        map.put("normalBehaviorDeduction", normalBehaviorDeduction);
        map.put("slowAttackScore", slowAttackScore);
        map.put("distributedAttackScore", distributedAttackScore);
        map.put("peakAdaptationScore", peakAdaptationScore);
        map.put("stateDurationScore", stateDurationScore);
        map.put("totalScore", getTotalScore());
        return map;
    }

    @Override
    public String toString() {
        return String.format("ScoreBreakdown{base=%d, freq=%d, div=%d, persist=%d, pattern=%d, " +
                "slow=%d, dist=%d, peak=%d, stateDur=%d, deduction=%d, total=%d}",
            baseScore, frequencyScore, diversityScore, persistenceScore, patternScore,
            slowAttackScore, distributedAttackScore, peakAdaptationScore, stateDurationScore,
            normalBehaviorDeduction, getTotalScore());
    }
}
