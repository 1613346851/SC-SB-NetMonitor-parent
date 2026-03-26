package com.network.gateway.service;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.confidence.ConfidenceService;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.StateInterventionDTO;
import com.network.gateway.dto.StateInterventionLog;
import com.network.gateway.event.StateTransitionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StateInterventionService {

    private static final Logger logger = LoggerFactory.getLogger(StateInterventionService.class);

    private final IpAttackStateCache stateCache;
    private final ConfidenceService confidenceService;
    private final StateTransitionEventPublisher eventPublisher;
    private final Map<String, List<StateInterventionLog>> interventionLogs = new ConcurrentHashMap<>();

    @Autowired
    public StateInterventionService(IpAttackStateCache stateCache,
                                    ConfidenceService confidenceService,
                                    StateTransitionEventPublisher eventPublisher) {
        this.stateCache = stateCache;
        this.confidenceService = confidenceService;
        this.eventPublisher = eventPublisher;
    }

    public void forceResetToNormal(String ip, String operator, String reason) {
        String normalizedIp = normalizeIp(ip);
        int fromState = stateCache.getState(normalizedIp);
        
        stateCache.resetToNormal(normalizedIp);
        confidenceService.resetConfidence(normalizedIp);
        
        StateInterventionLog log = new StateInterventionLog(
            normalizedIp, fromState, IpAttackStateConstant.NORMAL,
            StateInterventionDTO.TYPE_FORCE_RESET, operator, reason);
        recordInterventionLog(normalizedIp, log);
        
        eventPublisher.publishManualIntervention(normalizedIp, fromState, IpAttackStateConstant.NORMAL,
            operator, reason);
        
        logger.info("人工干预: 强制重置状态为NORMAL, ip={}, fromState={}, operator={}, reason={}", 
            normalizedIp, IpAttackStateConstant.getStateNameZh(fromState), operator, reason);
    }

    public void forceDefended(String ip, Long duration, String operator, String reason) {
        String normalizedIp = normalizeIp(ip);
        int fromState = stateCache.getState(normalizedIp);
        
        stateCache.updateState(normalizedIp, IpAttackStateConstant.DEFENDED);
        
        StateInterventionLog log = new StateInterventionLog(
            normalizedIp, fromState, IpAttackStateConstant.DEFENDED,
            StateInterventionDTO.TYPE_FORCE_DEFENDED, operator, reason);
        recordInterventionLog(normalizedIp, log);
        
        eventPublisher.publishManualIntervention(normalizedIp, fromState, IpAttackStateConstant.DEFENDED,
            operator, reason);
        
        logger.info("人工干预: 强制设置为DEFENDED, ip={}, fromState={}, duration={}, operator={}, reason={}", 
            normalizedIp, IpAttackStateConstant.getStateNameZh(fromState), duration, operator, reason);
    }

    public void forceBan(String ip, Long duration, String operator, String reason) {
        String normalizedIp = normalizeIp(ip);
        int fromState = stateCache.getState(normalizedIp);
        
        stateCache.updateState(normalizedIp, IpAttackStateConstant.DEFENDED);
        
        StateInterventionLog log = new StateInterventionLog(
            normalizedIp, fromState, IpAttackStateConstant.DEFENDED,
            StateInterventionDTO.TYPE_FORCE_BAN, operator, reason);
        recordInterventionLog(normalizedIp, log);
        
        eventPublisher.publishManualIntervention(normalizedIp, fromState, IpAttackStateConstant.DEFENDED,
            operator, reason);
        
        logger.info("人工干预: 紧急封禁, ip={}, fromState={}, duration={}, operator={}, reason={}", 
            normalizedIp, IpAttackStateConstant.getStateNameZh(fromState), duration, operator, reason);
    }

    public void batchResetToNormal(List<String> ips, String operator, String reason) {
        int successCount = 0;
        for (String ip : ips) {
            try {
                forceResetToNormal(ip, operator, reason);
                successCount++;
            } catch (Exception e) {
                logger.warn("批量重置失败: ip={}, error={}", ip, e.getMessage());
            }
        }
        
        logger.info("人工干预: 批量重置状态, total={}, success={}, operator={}, reason={}", 
            ips.size(), successCount, operator, reason);
    }

    public List<StateInterventionLog> getInterventionLogs(String ip) {
        String normalizedIp = normalizeIp(ip);
        return interventionLogs.getOrDefault(normalizedIp, new ArrayList<>());
    }

    public void clearInterventionLogs(String ip) {
        String normalizedIp = normalizeIp(ip);
        interventionLogs.remove(normalizedIp);
        logger.debug("清除干预日志: ip={}", normalizedIp);
    }

    public int getInterventionCount(String ip) {
        String normalizedIp = normalizeIp(ip);
        List<StateInterventionLog> logs = interventionLogs.get(normalizedIp);
        return logs != null ? logs.size() : 0;
    }

    private void recordInterventionLog(String ip, StateInterventionLog log) {
        interventionLogs.computeIfAbsent(ip, k -> new ArrayList<>()).add(log);
        
        List<StateInterventionLog> logs = interventionLogs.get(ip);
        if (logs.size() > 100) {
            logs.remove(0);
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP地址不能为空");
        }
        return ip.trim();
    }

    public String getStats() {
        return String.format("状态干预统计 - IP数:%d, 总干预次数:%d", 
            interventionLogs.size(), 
            interventionLogs.values().stream().mapToInt(List::size).sum());
    }
}
