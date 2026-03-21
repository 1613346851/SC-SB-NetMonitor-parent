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
        entry.updateState(newState);
        logger.debug("IP状态更新: ip={}, newState={}", normalizedIp, IpAttackStateConstant.getStateName(newState));
    }

    public void updateState(String ip, int newState, String eventId) {
        String normalizedIp = IpNormalizeUtil.normalize(ip);
        IpAttackStateEntry entry = getOrCreate(normalizedIp);
        entry.updateState(newState, eventId);
        logger.info("IP状态更新: ip={}, newState={}, eventId={}", normalizedIp, IpAttackStateConstant.getStateName(newState), eventId);
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

    public void incrementAttackCount(String ip) {
        IpAttackStateEntry entry = getOrCreate(ip);
        entry.incrementAttackCount();
    }

    public boolean isInDefendedState(String ip) {
        return getState(ip) == IpAttackStateConstant.DEFENDED;
    }

    public boolean shouldSkipTrafficPush(String ip) {
        IpAttackStateEntry entry = get(ip);
        return entry != null && entry.shouldSkipTrafficPush();
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
}
