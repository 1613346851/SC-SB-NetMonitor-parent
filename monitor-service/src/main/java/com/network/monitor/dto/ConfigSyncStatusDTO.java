package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSyncStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean gatewayConnected;

    private LocalDateTime lastSyncTime;

    private String lastSyncVersion;

    private Integer totalConfigCount;

    private Integer syncedConfigCount;

    private Long syncSuccessCount;

    private Long syncFailureCount;

    private String syncStatus;

    private String message;

    public static ConfigSyncStatusDTO success(int configCount) {
        return ConfigSyncStatusDTO.builder()
                .gatewayConnected(true)
                .lastSyncTime(LocalDateTime.now())
                .totalConfigCount(configCount)
                .syncedConfigCount(configCount)
                .syncStatus("SUCCESS")
                .message("配置同步成功")
                .build();
    }

    public static ConfigSyncStatusDTO failure(String message) {
        return ConfigSyncStatusDTO.builder()
                .gatewayConnected(false)
                .syncStatus("FAILURE")
                .message(message)
                .build();
    }

    public static ConfigSyncStatusDTO connecting() {
        return ConfigSyncStatusDTO.builder()
                .gatewayConnected(null)
                .syncStatus("CONNECTING")
                .message("正在连接网关...")
                .build();
    }
}
