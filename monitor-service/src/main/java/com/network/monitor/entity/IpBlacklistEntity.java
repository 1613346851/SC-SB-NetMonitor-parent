package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IpBlacklistEntity {

    private Long id;

    private String ipAddress;

    private String ipLocation;

    private LocalDateTime currentExpireTime;

    private Integer totalBanCount;

    private LocalDateTime firstBanTime;

    private LocalDateTime lastBanTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer status;

    public boolean isBanned() {
        return status != null && status == 1;
    }

    public boolean isExpired() {
        return currentExpireTime != null && LocalDateTime.now().isAfter(currentExpireTime);
    }

    public boolean isPermanent() {
        return currentExpireTime == null && isBanned();
    }
}
