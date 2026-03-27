package com.network.gateway.cache;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.confidence.ConfidenceContext;
import com.network.gateway.confidence.ConfidenceResult;
import com.network.gateway.confidence.ConfidenceService;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.event.StateTransitionEventPublisher;
import com.network.gateway.service.AttackIntensityCalculator;
import com.network.gateway.service.CooldownDurationCalculator;
import com.network.gateway.traffic.TrafficActivityService;
import com.network.gateway.util.IpNormalizeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IpAttackStateCache {

    private static final Logger logger = LoggerFactory.getLogger(IpAttackStateCache.class);

    private final Map<String, IpAttackStateEntry> stateMap = new ConcurrentHashMap<>();

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private StateTransitionEventPublisher eventPublisher;

    @Autowired
    private AttackIntensityCalculator intensityCalculator;

    @Autowired
    private CooldownDurationCalculator cooldownCalculator;

    @Autowired
    private ConfidenceService confidenceService;

    @Autowired
    private TrafficActivityService activityService;

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

    public StateTransitionResult checkAndTransitionState(String ip, int rateLimitCount, 
                                                          int suspiciousThreshold, int attackingThreshold) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = getOrCreate(normalizedIp);
        int currentState = entry.getState();
        
        StateTransitionResult result = new StateTransitionResult();
        result.setPreviousState(currentState);
        result.setNewState(currentState);

        if (rateLimitCount >= attackingThreshold) {
            return handleAttackingThreshold(normalizedIp, entry, currentState, result, rateLimitCount);
        }

        switch (currentState) {
            case IpAttackStateConstant.NORMAL:
                return handleNormalState(normalizedIp, entry, result, rateLimitCount, suspiciousThreshold);
            
            case IpAttackStateConstant.SUSPICIOUS:
                return handleSuspiciousState(normalizedIp, entry, result, rateLimitCount, suspiciousThreshold);
            
            case IpAttackStateConstant.ATTACKING:
                return handleAttackingState(normalizedIp, entry, result);
            
            case IpAttackStateConstant.DEFENDED:
                return handleDefendedState(normalizedIp, entry, result);
            
            case IpAttackStateConstant.COOLDOWN:
                return handleCooldownState(normalizedIp, entry, result, rateLimitCount);
            
            default:
                logger.warn("未知状态: ip={}, state={}", normalizedIp, currentState);
                return result;
        }
    }

    private StateTransitionResult handleAttackingThreshold(String ip, IpAttackStateEntry entry, 
                                                            int currentState, StateTransitionResult result,
                                                            int rateLimitCount) {
        if (currentState != IpAttackStateConstant.DEFENDED && 
            currentState != IpAttackStateConstant.COOLDOWN) {
            
            ConfidenceContext context = buildConfidenceContext(ip, entry, rateLimitCount, 
                configCache.getStateNormalToSuspiciousThresholdRps());
            ConfidenceResult confidenceResult = confidenceService.calculateForStateTransition(
                ip, currentState, IpAttackStateConstant.DEFENDED,
                context, IpAttackStateConstant.TRANSITION_REASON_RATE_LIMIT_THRESHOLD);
            
            int previousState = currentState;
            entry.updateState(IpAttackStateConstant.DEFENDED);
            entry.incrementAttackCount();
            entry.setConfidence(confidenceResult.getSmoothedConfidence());
            
            result.setNewState(IpAttackStateConstant.DEFENDED);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_RATE_LIMIT_THRESHOLD);
            
            eventPublisher.publishStateTransition(ip, previousState, IpAttackStateConstant.DEFENDED,
                    IpAttackStateConstant.TRANSITION_REASON_RATE_LIMIT_THRESHOLD, 
                    entry.getEventId(), confidenceResult.getSmoothedConfidence());
            
            logger.warn("IP状态转换: ip={}, {} -> DEFENDED, rateLimitCount={}, confidence={}, 达到防御阈值", 
                    ip, IpAttackStateConstant.getStateNameZh(currentState), rateLimitCount,
                    confidenceResult.getSmoothedConfidence());
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private StateTransitionResult handleNormalState(String ip, IpAttackStateEntry entry,
                                                     StateTransitionResult result, 
                                                     int rateLimitCount, int suspiciousThreshold) {
        int thresholdRps = configCache.getStateNormalToSuspiciousThresholdRps();
        
        if (rateLimitCount >= suspiciousThreshold || rateLimitCount >= thresholdRps) {
            ConfidenceContext context = buildConfidenceContext(ip, entry, rateLimitCount, thresholdRps);
            ConfidenceResult confidenceResult = confidenceService.calculateForStateTransition(
                ip, IpAttackStateConstant.NORMAL, IpAttackStateConstant.SUSPICIOUS,
                context, IpAttackStateConstant.TRANSITION_REASON_FREQUENCY_ABNORMAL);
            
            entry.updateState(IpAttackStateConstant.SUSPICIOUS);
            entry.startAttackTracking();
            entry.setConfidence(confidenceResult.getSmoothedConfidence());
            
            result.setNewState(IpAttackStateConstant.SUSPICIOUS);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_FREQUENCY_ABNORMAL);
            
            eventPublisher.publishAttackStart(ip, confidenceResult.getSmoothedConfidence());
            
            logger.warn("IP状态转换: ip={}, NORMAL -> SUSPICIOUS, rateLimitCount={}, confidence={}", 
                    ip, rateLimitCount, confidenceResult.getSmoothedConfidence());
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private StateTransitionResult handleSuspiciousState(String ip, IpAttackStateEntry entry,
                                                         StateTransitionResult result,
                                                         int rateLimitCount, int suspiciousThreshold) {
        long suspiciousDuration = entry.getStateDuration();
        long durationThreshold = configCache.getStateSuspiciousToAttackingDurationMs();
        int minRequests = configCache.getStateSuspiciousToAttackingMinRequests();
        int uriDiversityThreshold = configCache.getStateSuspiciousToAttackingUriDiversityThreshold();
        long quietDuration = configCache.getStateSuspiciousToNormalQuietDurationMs();
        
        boolean shouldAttack = suspiciousDuration >= durationThreshold &&
                               entry.getStateRequestCount() >= minRequests &&
                               entry.getUniqueUriCount() >= uriDiversityThreshold;
        
        if (shouldAttack) {
            ConfidenceContext context = buildConfidenceContext(ip, entry, rateLimitCount, 
                configCache.getStateNormalToSuspiciousThresholdRps());
            ConfidenceResult confidenceResult = confidenceService.calculateForStateTransition(
                ip, IpAttackStateConstant.SUSPICIOUS, IpAttackStateConstant.ATTACKING,
                context, IpAttackStateConstant.TRANSITION_REASON_ATTACK_CONFIRMED);
            
            entry.updateState(IpAttackStateConstant.ATTACKING);
            entry.setConfidence(confidenceResult.getSmoothedConfidence());
            
            result.setNewState(IpAttackStateConstant.ATTACKING);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_ATTACK_CONFIRMED);
            
            eventPublisher.publishStateTransition(ip, IpAttackStateConstant.SUSPICIOUS, 
                    IpAttackStateConstant.ATTACKING,
                    IpAttackStateConstant.TRANSITION_REASON_ATTACK_CONFIRMED,
                    entry.getEventId(), confidenceResult.getSmoothedConfidence());
            
            logger.warn("IP状态转换: ip={}, SUSPICIOUS -> ATTACKING, duration={}ms, requests={}, uris={}, confidence={}",
                    ip, suspiciousDuration, entry.getStateRequestCount(), entry.getUniqueUriCount(),
                    confidenceResult.getSmoothedConfidence());
        } else if (rateLimitCount < suspiciousThreshold && isQuietPeriod(entry, quietDuration)) {
            confidenceService.resetConfidence(ip);
            
            entry.updateState(IpAttackStateConstant.NORMAL);
            
            result.setNewState(IpAttackStateConstant.NORMAL);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_RECOVERY);
            
            eventPublisher.publishRecovery(ip, IpAttackStateConstant.SUSPICIOUS,
                    IpAttackStateConstant.TRANSITION_REASON_RECOVERY);
            
            logger.info("IP状态转换: ip={}, SUSPICIOUS -> NORMAL, 恢复正常", ip);
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private StateTransitionResult handleAttackingState(String ip, IpAttackStateEntry entry,
                                                        StateTransitionResult result) {
        long quietDuration = configCache.getStateDefendedToCooldownQuietDurationMs();
        
        if (isQuietPeriod(entry, quietDuration)) {
            entry.updateState(IpAttackStateConstant.NORMAL);
            
            result.setNewState(IpAttackStateConstant.NORMAL);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_RECOVERY);
            
            eventPublisher.publishRecovery(ip, IpAttackStateConstant.ATTACKING,
                    IpAttackStateConstant.TRANSITION_REASON_RECOVERY);
            
            logger.info("IP状态转换: ip={}, ATTACKING -> NORMAL, 攻击停止", ip);
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private StateTransitionResult handleDefendedState(String ip, IpAttackStateEntry entry,
                                                       StateTransitionResult result) {
        long quietDuration = configCache.getStateDefendedToCooldownQuietDurationMs();
        
        if (isQuietPeriod(entry, quietDuration)) {
            AttackContext context = entry.toAttackContext();
            int attackHistoryCount = entry.getAttackHistoryCount();
            long cooldownDuration = cooldownCalculator.calculateWithHistory(context, attackHistoryCount);
            
            entry.updateState(IpAttackStateConstant.COOLDOWN);
            entry.setDynamicCooldownDuration(cooldownDuration);
            entry.setAttackHistoryCount(attackHistoryCount + 1);
            
            result.setNewState(IpAttackStateConstant.COOLDOWN);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_ATTACK_STOPPED);
            
            eventPublisher.publishCooldownStart(ip, entry.getEventId());
            
            logger.info("IP状态转换: ip={}, DEFENDED -> COOLDOWN, cooldownDuration={}ms", 
                    ip, cooldownDuration);
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private StateTransitionResult handleCooldownState(String ip, IpAttackStateEntry entry,
                                                       StateTransitionResult result,
                                                       int rateLimitCount) {
        int reattackThreshold = configCache.getStateCooldownToAttackingThresholdRps();
        
        if (rateLimitCount >= reattackThreshold) {
            ConfidenceContext context = buildConfidenceContext(ip, entry, rateLimitCount, 
                configCache.getStateNormalToSuspiciousThresholdRps());
            ConfidenceResult confidenceResult = confidenceService.calculateForStateTransition(
                ip, IpAttackStateConstant.COOLDOWN, IpAttackStateConstant.ATTACKING,
                context, IpAttackStateConstant.TRANSITION_REASON_REATTACK);
            
            entry.updateState(IpAttackStateConstant.ATTACKING);
            entry.startAttackTracking();
            entry.setConfidence(confidenceResult.getSmoothedConfidence());
            
            result.setNewState(IpAttackStateConstant.ATTACKING);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_REATTACK);
            
            eventPublisher.publishStateTransition(ip, IpAttackStateConstant.COOLDOWN,
                    IpAttackStateConstant.ATTACKING,
                    IpAttackStateConstant.TRANSITION_REASON_REATTACK,
                    entry.getEventId(), confidenceResult.getSmoothedConfidence());
            
            logger.warn("IP状态转换: ip={}, COOLDOWN -> ATTACKING, 冷却期内再次攻击, confidence={}", 
                ip, confidenceResult.getSmoothedConfidence());
        } else if (!entry.isInCooldownPeriod()) {
            confidenceService.resetConfidenceOnCooldownEnd(ip);
            
            stateMap.remove(ip);
            
            result.setNewState(IpAttackStateConstant.NORMAL);
            result.setTransitioned(true);
            result.setReason(IpAttackStateConstant.TRANSITION_REASON_COOLDOWN_ENDED);
            
            eventPublisher.publishRecovery(ip, IpAttackStateConstant.COOLDOWN,
                    IpAttackStateConstant.TRANSITION_REASON_COOLDOWN_ENDED);
            
            logger.info("IP状态转换: ip={}, COOLDOWN -> NORMAL, 冷却结束", ip);
        }
        
        result.setStateRequestCount(entry.getAndResetStateRequestCount());
        return result;
    }

    private boolean isQuietPeriod(IpAttackStateEntry entry, long quietDurationMs) {
        long timeSinceLastRequest = System.currentTimeMillis() - entry.getLastRequestTime();
        return timeSinceLastRequest >= quietDurationMs;
    }

    private boolean isStillAttacking(String ip) {
        IpAttackStateEntry entry = stateMap.get(ip);
        if (entry == null) {
            return false;
        }
        long timeSinceLastRequest = System.currentTimeMillis() - entry.getLastRequestTime();
        return timeSinceLastRequest < 10000;
    }

    private ConfidenceContext buildConfidenceContext(String ip, IpAttackStateEntry entry, 
                                                      int currentRps, int thresholdRps) {
        ConfidenceContext context = new ConfidenceContext(ip);
        context.setCurrentState(entry.getState());
        context.setCurrentRps(currentRps);
        context.setThresholdRps(thresholdRps);
        context.setDurationMs(entry.getAttackDuration());
        context.setRequestCount(entry.getAttackRequestCount());
        context.setUniqueUriCount(entry.getUniqueUriCount());
        context.setUniqueUris(new HashSet<>(entry.getUniqueUris()));
        context.setHasAttackHistory(entry.getAttackHistoryCount() > 0);
        context.setAttackHistoryCount(entry.getAttackHistoryCount());
        context.setNormalRequestCount(entry.getRequestCount() - entry.getAttackRequestCount());
        context.setMatchedAttackPattern(false);
        return context;
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

    public void incrementRequestCount(String ip, String uri) {
        IpAttackStateEntry entry = getOrCreate(ip);
        entry.incrementRequestCount(uri);
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

    public Map<String, IpAttackStateEntry> getAllEntries() {
        return new ConcurrentHashMap<>(stateMap);
    }

    public IpAttackStateEntry getEntry(String ip) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        return stateMap.get(normalizedIp);
    }

    public void cleanExpiredEntries() {
        if (!activityService.isActive()) {
            return;
        }
        
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

    public List<IpAttackStateEntry> getEntriesByState(int state) {
        return stateMap.values().stream()
                .filter(entry -> entry.getState() == state)
                .collect(java.util.stream.Collectors.toList());
    }

    public int countByState(int state) {
        return (int) stateMap.values().stream()
                .filter(entry -> entry.getState() == state)
                .count();
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
