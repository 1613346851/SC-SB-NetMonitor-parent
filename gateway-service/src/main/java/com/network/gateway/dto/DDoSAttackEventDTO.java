package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DDoSAttackEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceIp;
    private String attackType;
    private String riskLevel;
    private int confidence;
    private int rateLimitCount;
    private String httpMethod;
    private String requestUri;
    private String userAgent;
    private String description;
    private Long timestamp;
    private String traceId;
    private String eventId;
    private int slidingWindowRps;
    private long attackDuration;
    private int uniqueUriCount;

    public DDoSAttackEventDTO(String sourceIp, int rateLimitCount) {
        this.sourceIp = sourceIp;
        this.attackType = "DDOS";
        this.riskLevel = "HIGH";
        this.confidence = 85;
        this.rateLimitCount = rateLimitCount;
        this.description = String.format("连续触发限流%d次，自动升级为DDoS攻击", rateLimitCount);
        this.timestamp = System.currentTimeMillis();
        this.traceId = generateTraceId();
        this.eventId = generateEventId();
    }

    public DDoSAttackEventDTO(String sourceIp, int rateLimitCount, int confidence) {
        this.sourceIp = sourceIp;
        this.attackType = "DDOS";
        this.riskLevel = calculateRiskLevel(confidence);
        this.confidence = confidence;
        this.rateLimitCount = rateLimitCount;
        this.description = String.format("连续触发限流%d次，置信度%d%%，自动升级为DDoS攻击", rateLimitCount, confidence);
        this.timestamp = System.currentTimeMillis();
        this.traceId = generateTraceId();
        this.eventId = generateEventId();
    }
    
    private String calculateRiskLevel(int confidence) {
        if (confidence >= 90) {
            return "CRITICAL";
        } else if (confidence >= 70) {
            return "HIGH";
        } else if (confidence >= 50) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateEventId() {
        return "DDOS_" + System.currentTimeMillis() + "_" + traceId;
    }

    public void updateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("检测到DDoS攻击行为: ");
        sb.append("限流次数=").append(rateLimitCount);
        if (slidingWindowRps > 0) {
            sb.append(", 滑动窗口RPS=").append(slidingWindowRps);
        }
        if (attackDuration > 0) {
            sb.append(", 持续时间=").append(attackDuration / 1000).append("s");
        }
        if (uniqueUriCount > 0) {
            sb.append(", URI数量=").append(uniqueUriCount);
        }
        this.description = sb.toString();
    }

    public String getSummary() {
        return String.format("DDoSAttackEventDTO{ip=%s, type=%s, confidence=%d, traceId=%s}",
            sourceIp, attackType, confidence, traceId);
    }
}
