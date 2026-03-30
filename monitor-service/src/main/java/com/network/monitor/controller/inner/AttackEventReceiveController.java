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
            
            log.info("接收到DDoS攻击事件：ip={}, attackType={}, riskLevel={}, rateLimitCount={}, reason={}", 
                sourceIp, attackType, riskLevel, rateLimitCount, reason);
            
            AttackEventEntity event = attackEventService.getOrCreateEvent(
                sourceIp, 
                attackType != null ? attackType : "DDOS", 
                riskLevel != null ? riskLevel : "HIGH", 
                confidence
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
                    
                    Object slidingWindowRpsObj = eventData.get("slidingWindowRps");
                    int slidingWindowRps = slidingWindowRpsObj != null ? 
                        ((Number) slidingWindowRpsObj).intValue() : rateLimitCount;
                    
                    Integer currentPeakRps = event.getPeakRps();
                    int newPeakRps = Math.max(currentPeakRps != null ? currentPeakRps : 0, slidingWindowRps);
                    
                    attackEventService.updateEventStatistics(
                        event.getId(), 
                        event.getTotalRequests() != null ? event.getTotalRequests() : 1, 
                        newPeakRps, 
                        confidence
                    );
                }
            }
            
            if ("执行防御".equals(reason) || "攻击确认，自动拉黑".equals(description)) {
                log.info("网关已执行防御操作，监测服务仅记录数据，不再生成防御决策：ip={}", sourceIp);
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
}
