package com.network.gateway.cache;

import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.util.IpNormalizeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IpAttackStateCache {

    private static final Logger logger = LoggerFactory.getLogger(IpAttackStateCache.class);

    private final Map<String, IpAttackStateEntry> stateMap = new ConcurrentHashMap<>();

    public IpAttackStateEntry getOrCreate(String ip) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        return stateMap.computeIfAbsent(normalizedIp, IpAttackStateEntry::new);
    }

    public IpAttackStateEntry get(String ip) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = stateMap.get(normalizedIp);
        if (entry != null && entry.isStateExpired()) {
            stateMap.remove(normalizedIp);
            return null;
        }
        return entry;
    }

    public int getState(String ip) {
        IpAttackStateEntry entry = get(ip);
        return entry != null ? entry.getState() : IpAttackStateConstant.NORMAL;
    }

    public void updateState(String ip, int newState) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = getOrCreate(normalizedIp);
        int oldState = entry.getState();
        entry.updateState(newState);
        logger.info("IP状态转换: ip={}, {} -> {}", normalizedIp, 
                IpAttackStateConstant.getStateNameZh(oldState), 
                IpAttackStateConstant.getStateNameZh(newState));
    }

    public void updateState(String ip, int newState, String eventId) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = getOrCreate(normalizedIp);
        int oldState = entry.getState();
        entry.updateState(newState, eventId);
        logger.info("IP状态转换: ip={}, {} -> {}, eventId={}", normalizedIp, 
                IpAttackStateConstant.getStateNameZh(oldState), 
                IpAttackStateConstant.getStateNameZh(newState), eventId);
    }

    public StateTransitionResult checkAndTransitionState(String ip, int rateLimitCount, int suspiciousThreshold, int attackingThreshold) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = getOrCreate(normalizedIp);
        int currentState = entry.getState();
        
        StateTransitionResult result = new StateTransitionResult();
        result.setPreviousState(currentState);
        result.setNewState(currentState);
        
        switch (currentState) {
            case IpAttackStateConstant.NORMAL:
                if (rateLimitCount >= suspiciousThreshold) {
                    entry.updateState(IpAttackStateConstant.SUSPICIOUS);
                    result.setNewState(IpAttackStateConstant.SUSPICIOUS);
                    result.setTransitioned(true);
                    result.setReason("请求频率异常，进入可疑状态");
                    logger.warn("IP状态转换: ip={}, NORMAL -> SUSPICIOUS, rateLimitCount={}", normalizedIp, rateLimitCount);
                }
                break;
                
            case IpAttackStateConstant.SUSPICIOUS:
                if (rateLimitCount >= attackingThreshold) {
                    entry.updateState(IpAttackStateConstant.ATTACKING);
                    result.setNewState(IpAttackStateConstant.ATTACKING);
                    result.setTransitioned(true);
                    result.setReason("持续高频请求，确认为攻击");
                    logger.warn("IP状态转换: ip={}, SUSPICIOUS -> ATTACKING, rateLimitCount={}", normalizedIp, rateLimitCount);
                } else if (rateLimitCount < suspiciousThreshold) {
                    long suspiciousDuration = entry.getStateDuration();
                    if (suspiciousDuration > 60000) {
                        entry.updateState(IpAttackStateConstant.NORMAL);
                        result.setNewState(IpAttackStateConstant.NORMAL);
                        result.setTransitioned(true);
                        result.setReason("可疑行为停止，恢复正常");
                        logger.info("IP状态转换: ip={}, SUSPICIOUS -> NORMAL, 恢复正常", normalizedIp);
                    }
                }
                break;
                
            case IpAttackStateConstant.ATTACKING:
                if (entry.getStateDuration() > IpAttackStateConstant.ATTACKING_DURATION_MS) {
                    entry.updateState(IpAttackStateConstant.DEFENDED);
                    result.setNewState(IpAttackStateConstant.DEFENDED);
                    result.setTransitioned(true);
                    result.setReason("攻击确认，执行防御");
                    logger.warn("IP状态转换: ip={}, ATTACKING -> DEFENDED, 执行防御", normalizedIp);
                }
                break;
                
            case IpAttackStateConstant.DEFENDED:
                if (!isStillAttacking(normalizedIp)) {
                    entry.updateState(IpAttackStateConstant.COOLDOWN);
                    result.setNewState(IpAttackStateConstant.COOLDOWN);
                    result.setTransitioned(true);
                    result.setReason("攻击停止，进入冷却期");
                    logger.info("IP状态转换: ip={}, DEFENDED -> COOLDOWN, 攻击停止", normalizedIp);
                }
                break;
                
            case IpAttackStateConstant.COOLDOWN:
                if (!entry.isInCooldownPeriod()) {
                    stateMap.remove(normalizedIp);
                    result.setNewState(IpAttackStateConstant.NORMAL);
                    result.setTransitioned(true);
                    result.setReason("冷却期结束，恢复正常");
                    logger.info("IP状态转换: ip={}, COOLDOWN -> NORMAL, 冷却结束", normalizedIp);
                }
                break;
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private boolean isStillAttacking(String ip) {
        IpAttackStateEntry entry = stateMap.get(ip);
        if (entry == null) {
            return false;
        }
        long timeSinceLastRequest = System.currentTimeMillis() - entry.getLastRequestTime();
        return timeSinceLastRequest < 10000;
    }

    public void markAsDefended(String ip, String eventId) {
        updateState(ip, IpAttackStateConstant.DEFENDED, eventId);
    }

    public void markAsSuspicious(String ip) {
        updateState(ip, IpAttackStateConstant.SUSPICIOUS);
    }

    public void markAsAttacking(String ip) {
        updateState(ip, IpAttackStateConstant.ATTACKING);
    }

    public void markAsCooldown(String ip) {
        updateState(ip, IpAttackStateConstant.COOLDOWN);
    }

    public void resetToNormal(String ip) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        stateMap.remove(normalizedIp);
        logger.debug("IP状态重置为NORMAL: ip={}", normalizedIp);
    }

    public void incrementRequestCount(String ip) {
        IpAttackStateEntry entry = getOrCreate(ip);
        entry.incrementRequestCount();
    }

    public void incrementRateLimitCount(String ip) {
        IpAttackStateEntry entry = getOrCreate(ip);
        entry.incrementRateLimitCount();
        if (entry.getState() == IpAttackStateConstant.NORMAL) {
            entry.updateState(IpAttackStateConstant.SUSPICIOUS);
        }
    }

    public int incrementRateLimitCount(String ip, long windowMs) {
        IpAttackStateEntry entry = getOrCreate(ip);
        int count = entry.incrementRateLimitCount(windowMs);
        return count;
    }

    public int getRateLimitCount(String ip) {
        IpAttackStateEntry entry = get(ip);
        return entry != null ? entry.getRateLimitCount() : 0;
    }

    public void resetRateLimitCount(String ip) {
        IpAttackStateEntry entry = get(ip);
        if (entry != null) {
            entry.resetRateLimitCount();
        }
    }

    public void incrementAttackCount(String ip) {
        IpAttackStateEntry entry = getOrCreate(ip);
        entry.incrementAttackCount();
    }

    public boolean isInDefendedState(String ip) {
        return getState(ip) == IpAttackStateConstant.DEFENDED;
    }

    public boolean shouldSkipTrafficPush(String ip) {
        return false;
    }

    public boolean shouldSkipDefenseAction(String ip) {
        IpAttackStateEntry entry = get(ip);
        return entry != null && entry.shouldSkipDefenseAction();
    }

    public boolean isInCooldown(String ip) {
        IpAttackStateEntry entry = get(ip);
        if (entry == null) {
            return false;
        }
        if (entry.getState() == IpAttackStateConstant.COOLDOWN) {
            if (entry.isInCooldownPeriod()) {
                return true;
            } else {
                resetToNormal(ip);
                return false;
            }
        }
        return false;
    }

    public boolean shouldExecuteDefense(String ip) {
        int state = getState(ip);
        return state == IpAttackStateConstant.ATTACKING;
    }

    public String getEventId(String ip) {
        IpAttackStateEntry entry = get(ip);
        return entry != null ? entry.getEventId() : null;
    }

    public int size() {
        return stateMap.size();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanExpiredEntries() {
        int removedCount = 0;
        for (Map.Entry<String, IpAttackStateEntry> entry : stateMap.entrySet()) {
            if (entry.getValue().isStateExpired()) {
                stateMap.remove(entry.getKey());
                removedCount++;
            }
        }
        if (removedCount > 0) {
            logger.debug("清理过期状态条目: removedCount={}, remaining={}", removedCount, stateMap.size());
        }
    }

    public void clear() {
        stateMap.clear();
        logger.info("IP攻击状态缓存已清空");
    }

    public static class StateTransitionResult {
        private int previousState;
        private int newState;
        private boolean transitioned;
        private String reason;
        private int stateRequestCount;

        public int getPreviousState() { return previousState; }
        public void setPreviousState(int previousState) { this.previousState = previousState; }
        public int getNewState() { return newState; }
        public void setNewState(int newState) { this.newState = newState; }
        public boolean isTransitioned() { return transitioned; }
        public void setTransitioned(boolean transitioned) { this.transitioned = transitioned; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public int getStateRequestCount() { return stateRequestCount; }
        public void setStateRequestCount(int stateRequestCount) { this.stateRequestCount = stateRequestCount; }
    }
}
