package com.network.gateway.service.impl;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.IpAttackStateEntry;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.event.StateTransitionEventPublisher;
import com.network.gateway.service.IpStateManualIntervention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IpStateManualInterventionImpl implements IpStateManualIntervention {

    private static final Logger logger = LoggerFactory.getLogger(IpStateManualInterventionImpl.class);

    private final Map<String, AtomicInteger> interventionCountMap = new ConcurrentHashMap<>();

    @Autowired
    private IpAttackStateCache stateCache;

    @Autowired
    private StateTransitionEventPublisher eventPublisher;

    @Override
    public void forceResetToNormal(String ip, String operator, String reason) {
        if (ip == null || ip.isEmpty()) {
            logger.warn("人工重置失败: IP地址为空");
            return;
        }

        IpAttackStateEntry entry = stateCache.getEntry(ip);
        int previousState = entry != null ? entry.getState() : IpAttackStateConstant.NORMAL;

        stateCache.resetToNormal(ip);

        incrementInterventionCount(ip);

        eventPublisher.publishManualIntervention(ip, previousState, IpAttackStateConstant.NORMAL, 
                operator, IpAttackStateConstant.TRANSITION_REASON_MANUAL_RESET);

        logger.info("人工重置IP状态: ip={}, previousState={}, operator={}, reason={}",
                ip, IpAttackStateConstant.getStateNameZh(previousState), operator, reason);
    }

    @Override
    public void forceDefended(String ip, Long duration, String operator, String reason) {
        if (ip == null || ip.isEmpty()) {
            logger.warn("人工封禁失败: IP地址为空");
            return;
        }

        IpAttackStateEntry entry = stateCache.getOrCreate(ip);
        int previousState = entry.getState();

        entry.updateState(IpAttackStateConstant.DEFENDED);
        if (duration != null && duration > 0) {
            entry.setDynamicCooldownDuration(duration * 1000);
        }

        incrementInterventionCount(ip);

        eventPublisher.publishManualIntervention(ip, previousState, IpAttackStateConstant.DEFENDED,
                operator, IpAttackStateConstant.TRANSITION_REASON_MANUAL_BAN);

        logger.info("人工封禁IP: ip={}, duration={}s, operator={}, reason={}",
                ip, duration, operator, reason);
    }

    @Override
    public void batchResetToNormal(List<String> ips, String operator, String reason) {
        if (ips == null || ips.isEmpty()) {
            logger.warn("批量重置失败: IP列表为空");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (String ip : ips) {
            try {
                forceResetToNormal(ip, operator, reason);
                successCount++;
            } catch (Exception e) {
                logger.error("批量重置单个IP失败: ip={}", ip, e);
                failCount++;
            }
        }

        logger.info("批量重置IP状态完成: total={}, success={}, fail={}, operator={}, reason={}",
                ips.size(), successCount, failCount, operator, reason);
    }

    @Override
    public void forceSetState(String ip, int targetState, String operator, String reason) {
        if (ip == null || ip.isEmpty()) {
            logger.warn("强制设置状态失败: IP地址为空");
            return;
        }

        if (!IpAttackStateConstant.isValidState(targetState)) {
            logger.warn("强制设置状态失败: 无效的目标状态 state={}", targetState);
            return;
        }

        IpAttackStateEntry entry = stateCache.getOrCreate(ip);
        int previousState = entry.getState();

        if (!IpAttackStateConstant.canTransitionTo(previousState, targetState)) {
            logger.warn("强制设置状态: 非法状态转换 ip={}, from={}, to={}, 强制执行",
                    ip, IpAttackStateConstant.getStateNameZh(previousState),
                    IpAttackStateConstant.getStateNameZh(targetState));
        }

        entry.updateState(targetState);

        incrementInterventionCount(ip);

        eventPublisher.publishManualIntervention(ip, previousState, targetState, operator, reason);

        logger.info("强制设置IP状态: ip={}, {} -> {}, operator={}, reason={}",
                ip, IpAttackStateConstant.getStateNameZh(previousState),
                IpAttackStateConstant.getStateNameZh(targetState), operator, reason);
    }

    @Override
    public int getManualInterventionCount(String ip) {
        AtomicInteger count = interventionCountMap.get(ip);
        return count != null ? count.get() : 0;
    }

    private void incrementInterventionCount(String ip) {
        interventionCountMap.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void cleanupInterventionHistory(String ip) {
        interventionCountMap.remove(ip);
    }

    public void cleanupAllInterventionHistory() {
        interventionCountMap.clear();
        logger.info("已清理所有人工干预历史记录");
    }
}
