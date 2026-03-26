package com.network.gateway.service;

import com.network.gateway.cache.AttackContext;
import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackIntensityCalculator {

    private static final Logger logger = LoggerFactory.getLogger(AttackIntensityCalculator.class);

    @Autowired
    private GatewayConfigCache configCache;

    public int calculate(AttackContext context) {
        if (context == null) {
            return 0;
        }

        int intensity = 0;

        int confidence = context.getConfidence();
        int confidenceMaxScore = configCache.getConfidenceFrequencyMaxScore();
        double confidenceContribution = confidence * 0.4;
        intensity += Math.min(confidenceMaxScore, (int) confidenceContribution);

        long durationSeconds = context.getDuration() / 1000;
        int persistenceMaxScore = configCache.getConfidencePersistenceMaxScore();
        int per10sScore = configCache.getConfidencePer10sScore();
        int durationContribution = (int) (durationSeconds / 10.0 * per10sScore);
        intensity += Math.min(persistenceMaxScore, durationContribution);

        int requestCount = context.getRequestCount();
        int requestContribution = Math.min(20, requestCount / 100 * 5);
        intensity += requestContribution;

        int uriDiversity = context.getUniqueUriCount();
        int diversityMaxScore = configCache.getConfidenceDiversityMaxScore();
        int perUriScore = configCache.getConfidencePerUriScore();
        int diversityContribution = uriDiversity * perUriScore;
        intensity += Math.min(diversityMaxScore, diversityContribution);

        int finalIntensity = Math.min(100, intensity);
        
        logger.debug("攻击强度计算: ip={}, confidence={}, duration={}s, requests={}, uris={}, intensity={}",
                context.getIp(), confidence, durationSeconds, requestCount, uriDiversity, finalIntensity);
        
        return finalIntensity;
    }

    public IntensityLevel getIntensityLevel(int intensity) {
        if (intensity >= 70) {
            return IntensityLevel.HIGH;
        } else if (intensity >= 40) {
            return IntensityLevel.MEDIUM;
        } else {
            return IntensityLevel.LOW;
        }
    }

    public enum IntensityLevel {
        LOW(0, "轻度"),
        MEDIUM(1, "中度"),
        HIGH(2, "重度");

        private final int level;
        private final String description;

        IntensityLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }
}
