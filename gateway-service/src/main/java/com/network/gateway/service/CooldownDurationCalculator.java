package com.network.gateway.service;

import com.network.gateway.cache.AttackContext;
import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CooldownDurationCalculator {

    private static final Logger logger = LoggerFactory.getLogger(CooldownDurationCalculator.class);

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private AttackIntensityCalculator intensityCalculator;

    public long calculate(AttackContext context) {
        if (context == null) {
            return getBaseDuration();
        }

        if (!isDynamicCooldownEnabled()) {
            return getBaseDuration();
        }

        int intensity = intensityCalculator.calculate(context);
        double intensityMultiplier = configCache.getCooldownIntensityMultiplier();
        
        long dynamicDuration = getBaseDuration() + (long)(intensity * intensityMultiplier * 1000);

        long result = Math.min(getMaxDuration(), dynamicDuration);

        logger.debug("冷却时长计算: ip={}, intensity={}, baseMs={}, dynamicMs={}, resultMs={}",
                context.getIp(), intensity, getBaseDuration(), dynamicDuration, result);

        return result;
    }

    public long calculateWithHistory(AttackContext context, int attackHistoryCount) {
        long baseResult = calculate(context);

        double historyMultiplier = configCache.getCooldownHistoryMultiplier();
        double historyMaxMultiplier = configCache.getCooldownHistoryMaxMultiplier();

        double multiplier = 1.0 + Math.min(historyMaxMultiplier - 1.0, attackHistoryCount * historyMultiplier);

        long result = Math.min(getMaxDuration(), (long)(baseResult * multiplier));

        logger.debug("冷却时长计算(含历史): ip={}, historyCount={}, multiplier={}, resultMs={}",
                context != null ? context.getIp() : "unknown", attackHistoryCount, multiplier, result);

        return result;
    }

    public long calculateFromIntensity(int intensity, int attackHistoryCount) {
        long baseDuration = getBaseDuration();
        double intensityMultiplier = configCache.getCooldownIntensityMultiplier();
        
        long dynamicDuration = baseDuration + (long)(intensity * intensityMultiplier * 1000);

        double historyMultiplier = configCache.getCooldownHistoryMultiplier();
        double historyMaxMultiplier = configCache.getCooldownHistoryMaxMultiplier();
        double multiplier = 1.0 + Math.min(historyMaxMultiplier - 1.0, attackHistoryCount * historyMultiplier);

        long result = Math.min(getMaxDuration(), (long)(dynamicDuration * multiplier));

        logger.debug("冷却时长计算(从强度): intensity={}, historyCount={}, resultMs={}",
                intensity, attackHistoryCount, result);

        return result;
    }

    private boolean isDynamicCooldownEnabled() {
        return configCache.isCooldownDynamicEnabled();
    }

    private long getBaseDuration() {
        return configCache.getCooldownBaseDurationMs();
    }

    private long getMaxDuration() {
        return configCache.getCooldownMaxDurationMs();
    }

    public String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, remainingSeconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}
