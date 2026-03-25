package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
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
            
            log.info("接收到DDoS攻击事件：ip={}, attackType={}, riskLevel={}, rateLimitCount={}", 
                sourceIp, attackType, riskLevel, rateLimitCount);
            
            AttackMonitorDTO attackDTO = new AttackMonitorDTO();
            attackDTO.setSourceIp(sourceIp);
            attackDTO.setAttackType(attackType != null ? attackType : "DDOS");
            attackDTO.setRiskLevel(riskLevel != null ? riskLevel : "HIGH");
            attackDTO.setConfidence(confidence);
            attackDTO.setTargetUri(requestUri);
            attackDTO.setAttackContent(description != null ? description : 
                String.format("连续触发限流%d次，自动升级为DDoS攻击", rateLimitCount));
            
            AttackMonitorEntity attackEntity = attackStoreService.convertToEntity(attackDTO);
            Long attackId = attackStoreService.saveAttack(attackEntity);
            
            if (attackId != null) {
                attackDTO.setAttackId(attackId);
                log.info("DDoS攻击记录已保存：attackId={}, ip={}", attackId, sourceIp);
            }
            
            defenseDecisionService.generateDefenseDecision(attackDTO);
            
            log.info("DDoS攻击事件处理完成：ip={}, 已生成防御决策", sourceIp);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理DDoS攻击事件失败：", e);
            return ApiResponse.error("处理DDoS攻击事件失败：" + e.getMessage());
        }
    }
}
