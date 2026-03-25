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
    private final Map<String, Long> windowStartMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> peakRpsMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

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

        long detectionWindowMs = sysConfigCache.getLongValue("ddos.detection.window-ms", 1000L);
        int ddosThreshold = sysConfigCache.getIntValue("ddos.threshold", 20);

        String currentWindow = getCurrentWindow(detectionWindowMs);
        String counterKey = sourceIp + "_" + currentWindow;

        long currentTime = System.currentTimeMillis();
        Long windowStart = windowStartMap.get(sourceIp);
        
        if (windowStart == null || (currentTime - windowStart) >= detectionWindowMs) {
            windowStartMap.put(sourceIp, currentTime);
            String newWindow = getCurrentWindow(detectionWindowMs);
            counterKey = sourceIp + "_" + newWindow;
            
            String finalCounterKey = counterKey;
            requestCounter.keySet().removeIf(key -> key.startsWith(sourceIp + "_") && !key.equals(finalCounterKey));
        }

        AtomicInteger count = requestCounter.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        updatePeakRps(sourceIp, currentCount);

        if (currentCount > ddosThreshold) {
            log.warn("检测到 DDoS 攻击！sourceIp={}, 当前窗口请求数={}, 阈值={}, 时间窗口={}ms", 
                    sourceIp, currentCount, ddosThreshold, detectionWindowMs);

            int currentState = attackStateCache.getState(sourceIp);
            if (currentState == IpAttackStateConstant.NORMAL || currentState == IpAttackStateConstant.SUSPICIOUS) {
                attackStateCache.markAsAttacking(sourceIp);
                log.info("IP状态更新为ATTACKING: ip={}, reason=ddos_detected", sourceIp);
            }

            AttackEventEntity event = attackEventService.getOrCreateEvent(
                sourceIp, AttackTypeConstant.DDOS, RiskLevelConstant.HIGH, 
                calculateConfidence(currentCount, ddosThreshold)
            );

            if (event != null) {
                int peakRps = getPeakRps(sourceIp);
                attackEventService.updateEventStatistics(event.getId(), currentCount, peakRps, 
                    calculateConfidence(currentCount, ddosThreshold));
                
                if (!attackStateCache.hasActiveEvent(sourceIp)) {
                    attackStateCache.setEventId(sourceIp, event.getEventId());
                }
            }

            AttackMonitorDTO dto = buildDDoSAttackDTO(trafficDTO, currentCount, ddosThreshold, detectionWindowMs);
            if (event != null) {
                dto.setEventId(event.getEventId());
            }
            return dto;
        }

        return null;
    }

    private int calculateConfidence(int requestCount, int threshold) {
        int excess = requestCount - threshold;
        return Math.min(90 + excess / 10, 100);
    }

    private void updatePeakRps(String sourceIp, int currentRps) {
        boolean recordEnabled = sysConfigCache.getBooleanValue("ddos.peak-rps.record.enabled", true);
        if (recordEnabled) {
            peakRpsMap.merge(sourceIp, currentRps, Math::max);
        }
    }

    private int getPeakRps(String sourceIp) {
        return peakRpsMap.getOrDefault(sourceIp, 0);
    }

    @Override
    public void resetCounter(String sourceIp, String timeWindow) {
        String key = sourceIp + "_" + timeWindow;
        requestCounter.remove(key);
        windowStartMap.remove(sourceIp);
        log.debug("重置 DDoS 计数器：key={}", key);
    }

    private String getCurrentWindow(long windowMs) {
        long windowStart = (System.currentTimeMillis() / windowMs) * windowMs;
        return String.valueOf(windowStart);
    }

    private AttackMonitorDTO buildDDoSAttackDTO(TrafficMonitorDTO trafficDTO, int requestCount, int ddosThreshold, long windowMs) {
        AttackMonitorDTO dto = new AttackMonitorDTO();
        
        dto.setTrafficId(null);
        dto.setAttackType(AttackTypeConstant.DDOS);
        dto.setRiskLevel(RiskLevelConstant.HIGH);
        dto.setConfidence(calculateConfidence(requestCount, ddosThreshold));
        dto.setRuleId(null);
        
        String windowDesc = windowMs >= 1000 ? (windowMs / 1000) + "秒" : windowMs + "毫秒";
        dto.setRuleContent(windowDesc + "内请求次数超过 " + ddosThreshold + " 次");
        dto.setSourceIp(trafficDTO.getSourceIp());
        dto.setTargetUri(trafficDTO.getRequestUri());
        dto.setAttackContent("请求频率：" + requestCount + " 次/" + windowDesc);
        
        return dto;
    }

    public void cleanExpiredCounters() {
        long detectionWindowMs = sysConfigCache.getLongValue("ddos.detection.window-ms", 1000L);
        String currentWindow = getCurrentWindow(detectionWindowMs);
        
        requestCounter.keySet().removeIf(key -> !key.endsWith("_" + currentWindow));
        
        long currentTime = System.currentTimeMillis();
        windowStartMap.keySet().removeIf(ip -> {
            Long start = windowStartMap.get(ip);
            return start != null && (currentTime - start) > detectionWindowMs * 2;
        });
        
        log.debug("清理过期 DDoS 计数器完成");
    }

    public int getCurrentRequestCount(String sourceIp) {
        long detectionWindowMs = sysConfigCache.getLongValue("ddos.detection.window-ms", 1000L);
        String currentWindow = getCurrentWindow(detectionWindowMs);
        String counterKey = sourceIp + "_" + currentWindow;
        
        AtomicInteger count = requestCounter.get(counterKey);
        return count != null ? count.get() : 0;
    }

    public int getPeakRpsForIp(String sourceIp) {
        return peakRpsMap.getOrDefault(sourceIp, 0);
    }

    public void clearPeakRps(String sourceIp) {
        peakRpsMap.remove(sourceIp);
    }
}
