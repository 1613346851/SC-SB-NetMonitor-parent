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
public class AttackChainDTO {

    private String ip;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalEvents;

    private List<TimelineEvent> timeline;

    private AttackSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private LocalDateTime time;
        private String eventType;
        private String eventTypeName;
        private String title;
        private String description;
        private String severity;
        private String attackType;
        private String defenseAction;
        private Long eventId;
        private String detailUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttackSummary {
        private Integer totalAttacks;
        private Integer totalDefenses;
        private Integer successfulDefenses;
        private Integer blockedRequests;
        private String mostFrequentAttackType;
        private String mostFrequentTarget;
        private Long totalDurationSeconds;
    }
}
