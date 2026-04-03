package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.AttackStoreService;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inner/attack")
public class AttackEventReceiveController {

    @Autowired
    private DefenseDecisionService defenseDecisionService;

    @Autowired
    private AttackStoreService attackStoreService;

    @Autowired
    private AttackEventService attackEventService;

    @PostMapping("/ddos-event")
    public ApiResponse<Void> receiveDDoSEvent(@RequestBody Map<String, Object> eventData) {
        try {
            String sourceIp = (String) eventData.get("sourceIp");
            String attackType = (String) eventData.get("attackType");
            String riskLevel = (String) eventData.get("riskLevel");
            Integer confidence = eventData.get("confidence") != null ? 
                ((Number) eventData.get("confidence")).intValue() : 85;
            Integer rateLimitCount = eventData.get("rateLimitCount") != null ? 
                ((Number) eventData.get("rateLimitCount")).intValue() : 0;
            String httpMethod = (String) eventData.get("httpMethod");
            String requestUri = (String) eventData.get("requestUri");
            String userAgent = (String) eventData.get("userAgent");
            String description = (String) eventData.get("description");
            String reason = (String) eventData.get("reason");
            String gatewayEventId = (String) eventData.get("eventId");
            Integer requestCount = eventData.get("requestCount") != null ? 
                ((Number) eventData.get("requestCount")).intValue() : null;
            Integer windowSeconds = eventData.get("windowSeconds") != null ? 
                ((Number) eventData.get("windowSeconds")).intValue() : null;
            
            Integer slidingWindowRps = eventData.get("slidingWindowRps") != null ?
                ((Number) eventData.get("slidingWindowRps")).intValue() : null;
            Integer peakRps = eventData.get("peakRps") != null ?
                ((Number) eventData.get("peakRps")).intValue() : null;
            Integer currentRps = eventData.get("currentRps") != null ?
                ((Number) eventData.get("currentRps")).intValue() : null;
            Integer attackDuration = eventData.get("attackDuration") != null ?
                ((Number) eventData.get("attackDuration")).intValue() : null;
            Integer requestCountFromGateway = eventData.get("requestCount") != null ?
                ((Number) eventData.get("requestCount")).intValue() : null;
            
            log.info("接收到DDoS攻击事件：ip={}, attackType={}, riskLevel={}, rateLimitCount={}, reason={}, eventId={}, peakRps={}, slidingWindowRps={}, requestCount={}", 
                sourceIp, attackType, riskLevel, rateLimitCount, reason, gatewayEventId, peakRps, slidingWindowRps, requestCountFromGateway);
            
            AttackEventEntity event = attackEventService.getOrCreateEventWithEventId(
                sourceIp, 
                attackType != null ? attackType : "DDOS", 
                riskLevel != null ? riskLevel : "HIGH", 
                confidence,
                gatewayEventId
            );
            
            String eventId = event != null ? event.getEventId() : null;
            
            AttackMonitorDTO attackDTO = new AttackMonitorDTO();
            attackDTO.setSourceIp(sourceIp);
            attackDTO.setAttackType(attackType != null ? attackType : "DDOS");
            attackDTO.setRiskLevel(riskLevel != null ? riskLevel : "HIGH");
            attackDTO.setConfidence(confidence);
            attackDTO.setTargetUri(requestUri);
            attackDTO.setEventId(eventId);
            attackDTO.setAttackContent(description != null ? description : 
                String.format("连续触发限流%d次，自动升级为DDoS攻击", rateLimitCount));
            
            AttackMonitorEntity attackEntity = attackStoreService.convertToEntity(attackDTO);
            Long attackId = attackStoreService.saveAttack(attackEntity);
            
            if (attackId != null) {
                attackDTO.setAttackId(attackId);
                log.info("DDoS攻击记录已保存：attackId={}, ip={}, eventId={}", attackId, sourceIp, eventId);
                
                if (event != null) {
                    attackEventService.incrementAttackCount(event.getId());
                    
                    int calculatedRps = calculateRps(eventData, rateLimitCount, requestCount, windowSeconds);
                    
                    log.info("计算RPS：eventId={}, peakRps={}, slidingWindowRps={}, rateLimitCount={}, requestCount={}, attackDuration={}, calculatedRps={}", 
                        eventId, peakRps, slidingWindowRps, rateLimitCount, requestCountFromGateway, attackDuration, calculatedRps);
                    
                    Integer currentPeakRps = event.getPeakRps();
                    int newPeakRps = Math.max(currentPeakRps != null ? currentPeakRps : 0, calculatedRps);
                    
                    int currentTotal = event.getTotalRequests() != null ? event.getTotalRequests() : 0;
                    int newTotal = requestCount != null ? currentTotal + requestCount : currentTotal + 1;
                    
                    Integer currentConfidenceEnd = event.getConfidenceEnd();
                    int newConfidence = Math.max(currentConfidenceEnd != null ? currentConfidenceEnd : 0, confidence);
                    
                    attackEventService.updateEventStatistics(
                        event.getId(), 
                        newTotal, 
                        newPeakRps, 
                        newConfidence
                    );
                    
                    log.info("更新事件统计完成：eventId={}, peakRps={}, totalRequests={}, confidence={} (original={})", 
                        eventId, newPeakRps, newTotal, newConfidence, confidence);
                }
            }
            
            if ("执行防御".equals(reason) || "攻击确认".equals(reason) || "攻击确认，自动拉黑".equals(description)) {
                log.info("网关已执行或正在执行防御操作，监测服务仅记录数据，不再生成防御决策：ip={}, reason={}", sourceIp, reason);
            } else {
                defenseDecisionService.generateDefenseDecision(attackDTO);
                log.info("DDoS攻击事件处理完成：ip={}, 已生成防御决策", sourceIp);
            }
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理DDoS攻击事件失败：", e);
            return ApiResponse.error("处理DDoS攻击事件失败：" + e.getMessage());
        }
    }
    
    private int calculateRps(Map<String, Object> eventData, int rateLimitCount, Integer requestCount, Integer windowSeconds) {
        Object peakRpsObj = eventData.get("peakRps");
        if (peakRpsObj != null) {
            int peakRps = ((Number) peakRpsObj).intValue();
            if (peakRps > 0) {
                return peakRps;
            }
        }
        
        Object slidingWindowRpsObj = eventData.get("slidingWindowRps");
        if (slidingWindowRpsObj != null) {
            int slidingRps = ((Number) slidingWindowRpsObj).intValue();
            if (slidingRps > 0) {
                return slidingRps;
            }
        }
        
        Object currentRpsObj = eventData.get("currentRps");
        if (currentRpsObj != null) {
            int currentRps = ((Number) currentRpsObj).intValue();
            if (currentRps > 0) {
                return currentRps;
            }
        }
        
        if (requestCount != null && windowSeconds != null && windowSeconds > 0) {
            int calculatedRps = requestCount / windowSeconds;
            if (calculatedRps > 0) {
                return calculatedRps;
            }
        }
        
        Object attackDurationMsObj = eventData.get("attackDuration");
        Object requestCountObj = eventData.get("requestCount");
        if (attackDurationMsObj != null && requestCountObj != null) {
            long attackDurationMs = ((Number) attackDurationMsObj).longValue();
            int reqCount = ((Number) requestCountObj).intValue();
            if (attackDurationMs > 0 && reqCount > 0) {
                int calculatedRps = (int) (reqCount * 1000 / attackDurationMs);
                if (calculatedRps > 0) {
                    return calculatedRps;
                }
            }
        }
        
        if (rateLimitCount > 0) {
            return rateLimitCount;
        }
        
        Object attackCountObj = eventData.get("attackCount");
        if (attackCountObj != null) {
            int attackCount = ((Number) attackCountObj).intValue();
            if (attackCount > 0) {
                return attackCount;
            }
        }
        
        return 1;
    }
}
