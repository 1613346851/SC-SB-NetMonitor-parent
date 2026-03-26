package com.network.gateway.confidence;

import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceCalculator.class);

    @Autowired
    private GatewayConfigCache configCache;

    public void setConfigCache(GatewayConfigCache configCache) {
        this.configCache = configCache;
    }

    public int calculate(ConfidenceContext context) {
        if (context == null) {
            return 0;
        }

        ScoreBreakdown breakdown = calculateBreakdown(context);
        int totalScore = breakdown.getTotalScore();

        logger.debug("置信度计算: ip={}, total={}, breakdown={}", 
            context.getIp(), totalScore, breakdown);

        return totalScore;
    }

    public ScoreBreakdown calculateBreakdown(ConfidenceContext context) {
        ScoreBreakdown breakdown = new ScoreBreakdown();

        breakdown.setBaseScore(calculateBaseScore());
        breakdown.setFrequencyScore(calculateFrequencyScore(context));
        breakdown.setDiversityScore(calculateDiversityScore(context));
        breakdown.setPersistenceScore(calculatePersistenceScore(context));
        breakdown.setPatternScore(calculatePatternScore(context));
        breakdown.setNormalBehaviorDeduction(calculateNormalBehaviorDeduction(context));

        return breakdown;
    }

    private int calculateBaseScore() {
        int baseScore = configCache.getConfidenceBaseScore();
        return Math.min(30, Math.max(0, baseScore));
    }

    private int calculateFrequencyScore(ConfidenceContext context) {
        int maxScore = configCache.getConfidenceFrequencyMaxScore();
        int perExceedScore = configCache.getConfidencePerExceedScore();

        double exceedRatio = context.getExceedRatio();
        if (exceedRatio <= 1.0) {
            return 0;
        }

        int exceedTimes = (int) Math.floor(exceedRatio - 1);
        int score = exceedTimes * perExceedScore;

        return Math.min(maxScore, Math.max(0, score));
    }

    private int calculateDiversityScore(ConfidenceContext context) {
        int maxScore = configCache.getConfidenceDiversityMaxScore();
        int perUriScore = configCache.getConfidencePerUriScore();

        int uniqueUriCount = context.getUniqueUriCount();
        if (uniqueUriCount <= 1) {
            return 0;
        }

        int score = (uniqueUriCount - 1) * perUriScore;

        return Math.min(maxScore, Math.max(0, score));
    }

    private int calculatePersistenceScore(ConfidenceContext context) {
        int maxScore = configCache.getConfidencePersistenceMaxScore();
        int per10sScore = configCache.getConfidencePer10sScore();

        double durationSeconds = context.getDurationSeconds();
        if (durationSeconds < 10) {
            return 0;
        }

        int duration10s = (int) Math.floor(durationSeconds / 10);
        int score = duration10s * per10sScore;

        return Math.min(maxScore, Math.max(0, score));
    }

    private int calculatePatternScore(ConfidenceContext context) {
        int maxScore = configCache.getConfidencePatternMaxScore();

        if (!context.isMatchedAttackPattern()) {
            return 0;
        }

        return Math.min(maxScore, 10);
    }

    private int calculateNormalBehaviorDeduction(ConfidenceContext context) {
        int maxDeduction = configCache.getConfidenceNormalBehaviorMaxDeduction();
        int noHistoryDeduction = configCache.getConfidenceNormalBehaviorNoHistoryDeduction();
        int normalRequestsDeduction = configCache.getConfidenceNormalBehaviorNormalRequestsDeduction();

        int totalDeduction = 0;

        if (!context.isHasAttackHistory()) {
            totalDeduction += noHistoryDeduction;
        }

        if (context.hasNormalHistory()) {
            totalDeduction += normalRequestsDeduction;
        }

        return Math.min(maxDeduction, totalDeduction);
    }

    public ConfidenceResult calculateWithBreakdown(ConfidenceContext context) {
        ConfidenceResult result = new ConfidenceResult();
        result.setIp(context.getIp());
        result.setRawConfidence(calculate(context));
        result.setBreakdown(calculateBreakdown(context));
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}
