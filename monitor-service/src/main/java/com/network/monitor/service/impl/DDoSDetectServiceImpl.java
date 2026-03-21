package com.network.monitor.service.impl;

import com.network.monitor.cache.IpAttackStateCache;
import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.common.constant.AttackTypeConstant;
import com.network.monitor.common.constant.IpAttackStateConstant;
import com.network.monitor.common.constant.RiskLevelConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.DDoSDetectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DDoSDetectServiceImpl implements DDoSDetectService {

    @Autowired
    private SysConfigCache sysConfigCache;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private AttackEventService attackEventService;

    private final Map<String, AtomicInteger> requestCounter = new ConcurrentHashMap<>();
    private final Map<String, Integer> peakRpsMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Override
    public AttackMonitorDTO detect(TrafficMonitorDTO trafficDTO) {
        if (trafficDTO == null || trafficDTO.getSourceIp() == null) {
            return null;
        }

        String sourceIp = trafficDTO.getSourceIp();

        if (attackStateCache.isInDefendedState(sourceIp)) {
            log.debug("IP已处于DEFENDED状态，跳过DDoS检测: ip={}", sourceIp);
            return null;
        }

        String currentTimeWindow = getCurrentMinuteWindow();
        String counterKey = sourceIp + "_" + currentTimeWindow;

        AtomicInteger count = requestCounter.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        updatePeakRps(sourceIp, currentCount);

        int ddosThreshold = sysConfigCache.getIntValue("ddos.threshold", 100);

        if (currentCount > ddosThreshold) {
            log.warn("检测到 DDoS 攻击！sourceIp={}, 当前窗口请求数={}, 阈值={}", 
                    sourceIp, currentCount, ddosThreshold);

            int currentState = attackStateCache.getState(sourceIp);
            if (currentState == IpAttackStateConstant.NORMAL || currentState == IpAttackStateConstant.SUSPICIOUS) {
                attackStateCache.markAsAttacking(sourceIp);
                log.info("IP状态更新为ATTACKING: ip={}, reason=ddos_detected", sourceIp);
            }

            AttackEventEntity event = attackEventService.getOrCreateEvent(
                sourceIp, AttackTypeConstant.DDOS, RiskLevelConstant.HIGH, 
                Math.min(90 + (currentCount - ddosThreshold) / 10, 100)
            );

            if (event != null) {
                int peakRps = getPeakRps(sourceIp);
                attackEventService.updateEventStatistics(event.getId(), currentCount, peakRps, 
                    Math.min(90 + (currentCount - ddosThreshold) / 10, 100));
                
                if (!attackStateCache.hasActiveEvent(sourceIp)) {
                    attackStateCache.setEventId(sourceIp, event.getEventId());
                }
            }

            AttackMonitorDTO dto = buildDDoSAttackDTO(trafficDTO, currentCount, ddosThreshold);
            if (event != null) {
                dto.setEventId(event.getEventId());
            }
            return dto;
        }

        return null;
    }

    private void updatePeakRps(String sourceIp, int currentRps) {
        peakRpsMap.merge(sourceIp, currentRps, Math::max);
    }

    private int getPeakRps(String sourceIp) {
        return peakRpsMap.getOrDefault(sourceIp, 0);
    }

    @Override
    public void resetCounter(String sourceIp, String timeWindow) {
        String key = sourceIp + "_" + timeWindow;
        requestCounter.remove(key);
        log.debug("重置 DDoS 计数器：key={}", key);
    }

    private String getCurrentMinuteWindow() {
        return LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    private AttackMonitorDTO buildDDoSAttackDTO(TrafficMonitorDTO trafficDTO, int requestCount, int ddosThreshold) {
        AttackMonitorDTO dto = new AttackMonitorDTO();
        
        dto.setTrafficId(null);
        dto.setAttackType(AttackTypeConstant.DDOS);
        dto.setRiskLevel(RiskLevelConstant.HIGH);
        dto.setConfidence(Math.min(90 + (requestCount - ddosThreshold) / 10, 100));
        dto.setRuleId(null);
        dto.setRuleContent("1 分钟内请求次数超过 " + ddosThreshold + " 次");
        dto.setSourceIp(trafficDTO.getSourceIp());
        dto.setTargetUri(trafficDTO.getRequestUri());
        dto.setAttackContent("请求频率：" + requestCount + " 次/分钟");
        
        return dto;
    }

    public void cleanExpiredCounters() {
        String currentWindow = getCurrentMinuteWindow();
        
        requestCounter.keySet().removeIf(key -> {
            return !key.endsWith("_" + currentWindow);
        });
        
        log.info("清理过期 DDoS 计数器完成");
    }
}
