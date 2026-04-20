package com.network.gateway.confidence;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfidenceSmoother {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceSmoother.class);

    private static final String STRATEGY_ONLY_UP = "ONLY_UP";
    private static final String STRATEGY_SLIDING_AVERAGE = "SLIDING_AVERAGE";

    @Autowired
    private GatewayConfigCache configCache;

    public void setConfigCache(GatewayConfigCache configCache) {
        this.configCache = configCache;
    }

    private final Map<String, Integer> lastConfidence = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastState = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("置信度平滑处理器初始化完成，策略: {}", getStrategy());
    }

    public int smooth(String ip, int rawConfidence) {
        return smooth(ip, rawConfidence, IpAttackStateConstant.NORMAL);
    }

    public int smooth(String ip, int rawConfidence, int currentState) {
        if (ip == null || ip.isEmpty()) {
            return rawConfidence;
        }

        Integer lastStateValue = lastState.get(ip);
        if (lastStateValue != null && lastStateValue == IpAttackStateConstant.COOLDOWN 
            && currentState == IpAttackStateConstant.NORMAL) {
            reset(ip);
            logger.debug("IP从COOLDOWN恢复到NORMAL，重置置信度: ip={}", ip);
        }

        Integer last = lastConfidence.get(ip);
        if (last == null) {
            lastConfidence.put(ip, rawConfidence);
            lastUpdateTime.put(ip, System.currentTimeMillis());
            lastState.put(ip, currentState);
            return rawConfidence;
        }

        String strategy = getStrategy();
        int result;

        switch (strategy.toUpperCase()) {
            case STRATEGY_ONLY_UP:
                result = smoothOnlyUp(ip, rawConfidence, last);
                break;
            case STRATEGY_SLIDING_AVERAGE:
                result = smoothSlidingAverage(ip, rawConfidence, last);
                break;
            default:
                result = rawConfidence;
        }

        lastConfidence.put(ip, result);
        lastUpdateTime.put(ip, System.currentTimeMillis());
        lastState.put(ip, currentState);

        if (result != rawConfidence) {
            logger.debug("置信度平滑处理: ip={}, raw={}, smoothed={}, strategy={}", 
                ip, rawConfidence, result, strategy);
        }

        return result;
    }

    private int smoothOnlyUp(String ip, int rawConfidence, int lastConfidence) {
        int minConfidence = configCache.getConfidenceMinValue();
        int result = Math.max(rawConfidence, lastConfidence);
        result = Math.max(result, minConfidence);
        return Math.min(100, Math.max(0, result));
    }

    private int smoothSlidingAverage(String ip, int rawConfidence, int lastConfidence) {
        double alpha = getAlpha();
        int smoothed = (int) (alpha * rawConfidence + (1 - alpha) * lastConfidence);
        int minConfidence = configCache.getConfidenceMinValue();
        smoothed = Math.max(smoothed, minConfidence);
        return Math.min(100, Math.max(0, smoothed));
    }

    public void reset(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        lastConfidence.remove(ip);
        lastUpdateTime.remove(ip);
        lastState.remove(ip);

        logger.debug("置信度已重置: ip={}", ip);
    }

    public void resetAll() {
        int count = lastConfidence.size();
        lastConfidence.clear();
        lastUpdateTime.clear();
        lastState.clear();

        logger.info("所有置信度已重置，共{}条", count);
    }

    public Integer getLastConfidence(String ip) {
        return lastConfidence.get(ip);
    }

    public Long getLastUpdateTime(String ip) {
        return lastUpdateTime.get(ip);
    }

    public Integer getLastState(String ip) {
        return lastState.get(ip);
    }

    public int size() {
        return lastConfidence.size();
    }

    private String getStrategy() {
        return configCache.getConfidenceSmoothStrategy();
    }

    private double getAlpha() {
        return configCache.getConfidenceSmoothAlpha();
    }

    public void setStrategy(String strategy) {
        if (STRATEGY_ONLY_UP.equalsIgnoreCase(strategy) || 
            STRATEGY_SLIDING_AVERAGE.equalsIgnoreCase(strategy)) {
            configCache.updateConfig("confidence.smooth.strategy", strategy.toUpperCase());
            logger.info("置信度平滑策略已更新: {}", strategy.toUpperCase());
        }
    }

    public void setAlpha(double alpha) {
        if (alpha > 0 && alpha <= 1) {
            configCache.updateConfig("confidence.smooth.alpha", String.valueOf(alpha));
            logger.info("置信度滑动平均系数已更新: {}", alpha);
        }
    }

    public String getStats() {
        return String.format("置信度平滑统计 - 策略:%s, 缓存数:%d, alpha:%.2f", 
            getStrategy(), size(), getAlpha());
    }
}
