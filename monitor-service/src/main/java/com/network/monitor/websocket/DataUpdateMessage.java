package com.network.monitor.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataUpdateMessage {

    private String type;

    private Object data;

    private LocalDateTime timestamp;

    public static DataUpdateMessage attackRecord(Object attackData) {
        return DataUpdateMessage.builder()
            .type("ATTACK_RECORD")
            .data(attackData)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static DataUpdateMessage alertRecord(Object alertData) {
        return DataUpdateMessage.builder()
            .type("ALERT_RECORD")
            .data(alertData)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static DataUpdateMessage statsUpdate(Object statsData) {
        return DataUpdateMessage.builder()
            .type("STATS_UPDATE")
            .data(statsData)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static DataUpdateMessage eventStatsUpdate(Object eventStats) {
        return DataUpdateMessage.builder()
            .type("EVENT_STATS_UPDATE")
            .data(eventStats)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
