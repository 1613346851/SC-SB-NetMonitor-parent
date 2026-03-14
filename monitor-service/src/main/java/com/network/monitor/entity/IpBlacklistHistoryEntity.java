package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IpBlacklistHistoryEntity {

    private Long id;

    private Long blacklistId;

    private Long attackId;

    private Long trafficId;

    private Long ruleId;

    private String banType;

    private String banReason;

    private Long banDuration;

    private LocalDateTime expireTime;

    private Integer processStatus;

    private String operator;

    private LocalDateTime banExecuteTime;

    private LocalDateTime unbanExecuteTime;

    private String unbanReason;

    private LocalDateTime createTime;

    public boolean isBanning() {
        return processStatus != null && processStatus == 1;
    }

    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    public boolean isPermanent() {
        return expireTime == null && isBanning();
    }
}
