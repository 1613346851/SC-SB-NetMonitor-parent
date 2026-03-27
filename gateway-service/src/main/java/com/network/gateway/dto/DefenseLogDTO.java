package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefenseLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;

    private Long attackId;

    private Long trafficId;

    private String defenseType;

    private String defenseAction;

    private String defenseTarget;

    private String defenseReason;

    private String expireTime;

    private Integer executeStatus;

    private String executeResult;

    private String operator;

    private Integer confidence;

    private Long executeTime;

    private Long processingTime;

    private String requestUri;

    private String httpMethod;

    private Integer rateLimitCount;

    private String timeWindow;

    private String traceId;

    private String deduplicationKey;

    private int fromState;

    private int toState;

    private int totalRequests;

    private int blockedRequests;

    private String stateDuration;

    private int retryCount;

    private String batchId;

    public DefenseLogDTO(String defenseType, String defenseTarget, String defenseReason) {
        this.defenseType = defenseType;
        this.defenseTarget = defenseTarget;
        this.defenseReason = defenseReason;
        this.executeStatus = 1;
        this.operator = "SYSTEM";
        this.traceId = generateTraceId();
        this.deduplicationKey = generateDeduplicationKey();
        this.retryCount = 0;
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateDeduplicationKey() {
        return defenseTarget + "_" + defenseType + "_" + System.currentTimeMillis() / 60000;
    }

    public void setExecutionResult(boolean success, String executeResult) {
        this.executeStatus = success ? 1 : 0;
        this.executeResult = executeResult;
    }

    public void setExpireTimestamp(Long expireTimestamp) {
        if (expireTimestamp != null) {
            this.expireTime = String.valueOf(expireTimestamp);
        }
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isDuplicate(DefenseLogDTO other) {
        if (other == null) {
            return false;
        }
        return this.deduplicationKey != null && 
               this.deduplicationKey.equals(other.deduplicationKey);
    }

    public String getSummary() {
        return String.format("DefenseLogDTO{eventId=%s, type=%s, target=%s, confidence=%d, traceId=%s}",
            eventId, defenseType, defenseTarget, confidence != null ? confidence : 0, traceId);
    }
}
