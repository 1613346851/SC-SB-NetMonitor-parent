package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private String banType;
    private String banReason;
    private Long duration;
    private Long expireTimestamp;
    private String operator;
    private Long timestamp;
    private String traceId;
    private int fromState;
    private int toState;
    private int confidence;
    private String eventId;

    public BlacklistEventDTO(String ip, String banType, String banReason) {
        this.ip = ip;
        this.banType = banType != null ? banType : "SYSTEM";
        this.banReason = banReason;
        this.operator = "SYSTEM";
        this.timestamp = System.currentTimeMillis();
        this.traceId = generateTraceId();
        this.eventId = generateEventId();
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateEventId() {
        return "BL_" + System.currentTimeMillis() + "_" + traceId;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.duration = durationSeconds;
        if (durationSeconds != null && durationSeconds > 0) {
            this.expireTimestamp = System.currentTimeMillis() + (durationSeconds * 1000);
        }
    }

    public boolean isPermanent() {
        return duration == null || duration <= 0;
    }

    public String getSummary() {
        return String.format("BlacklistEventDTO{ip=%s, type=%s, reason=%s, traceId=%s}",
            ip, banType, banReason, traceId);
    }
}
