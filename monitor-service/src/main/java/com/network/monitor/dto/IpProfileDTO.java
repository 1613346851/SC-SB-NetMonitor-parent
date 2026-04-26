package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpProfileDTO {

    private String ip;

    private GeoIpDTO geoInfo;

    private Integer riskScore;

    private String riskLevel;

    private String currentStatus;

    private Integer currentState;

    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;

    private Long totalAttackCount;

    private Long totalBlockCount;

    private Long totalRequestCount;

    private Long totalTrafficBytes;

    private List<AttackTypeStats> attackTypeStats;

    private List<HourlyStats> hourlyStats;

    private List<DailyStats> dailyStats;

    private LocalDateTime lastAttackTime;

    private String lastAttackType;

    private Boolean isBlacklisted;

    private LocalDateTime blacklistedAt;

    private LocalDateTime blacklistExpireTime;

    private String blacklistReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttackTypeStats {
        private String attackType;
        private String attackTypeName;
        private Long count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyStats {
        private Integer hour;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private String date;
        private Long attackCount;
        private Long requestCount;
    }
}
