package com.network.monitor.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class BlacklistEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private String banType;
    private String banReason;
    private Long duration;
    private String operator;
    private Long timestamp;
    private String eventId;
    private Integer confidence;
    private String attackType;

    public boolean isSystemBan() {
        return "SYSTEM".equals(banType);
    }

    public boolean isManualBan() {
        return "MANUAL".equals(banType);
    }

    public boolean isPermanent() {
        return duration == null || duration <= 0;
    }

    @Override
    public String toString() {
        return String.format("BlacklistEventDTO{ip=%s, banType=%s, reason=%s, duration=%s}", 
            ip, banType, banReason, duration);
    }
}
