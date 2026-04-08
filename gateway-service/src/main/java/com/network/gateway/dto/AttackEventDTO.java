package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttackEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceIp;
    private String attackType;
    private String riskLevel;
    private int confidence;
    private String eventId;
    
    private String ruleName;
    private String ruleId;
    private String attackContent;
    private String targetUri;
    private String httpMethod;
    private String userAgent;
    
    private String requestId;
    private Map<String, String> queryParams;
    private Map<String, String> requestHeaders;
    private String requestBody;
    
    private String description;
    private Long timestamp;
    private String traceId;

    public AttackEventDTO(String sourceIp, String attackType, String riskLevel, int confidence) {
        this.sourceIp = sourceIp;
        this.attackType = attackType;
        this.riskLevel = riskLevel;
        this.confidence = confidence;
        this.timestamp = System.currentTimeMillis();
        this.traceId = generateTraceId();
        this.eventId = generateEventId(attackType);
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateEventId(String attackType) {
        String prefix = attackType != null ? attackType : "ATTACK";
        return prefix + "_" + System.currentTimeMillis() + "_" + traceId;
    }

    public void updateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("检测到").append(attackType).append("攻击: ");
        sb.append("规则=").append(ruleName != null ? ruleName : "未知");
        sb.append(", 风险等级=").append(riskLevel);
        sb.append(", 置信度=").append(confidence).append("%");
        if (attackContent != null && !attackContent.isEmpty()) {
            sb.append(", 攻击内容=").append(attackContent.length() > 50 ? 
                attackContent.substring(0, 50) + "..." : attackContent);
        }
        this.description = sb.toString();
    }

    public String getSummary() {
        return String.format("AttackEventDTO{ip=%s, type=%s, risk=%s, confidence=%d, rule=%s, traceId=%s}",
            sourceIp, attackType, riskLevel, confidence, ruleName, traceId);
    }
}
