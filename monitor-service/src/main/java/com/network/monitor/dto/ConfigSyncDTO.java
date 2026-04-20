package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSyncDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;

    private LocalDateTime syncTime;

    private Integer configCount;

    private Map<String, String> configs;

    private String syncType;

    private String source;

    public static ConfigSyncDTO fullSync(Map<String, String> configs) {
        return ConfigSyncDTO.builder()
                .version(generateVersion())
                .syncTime(LocalDateTime.now())
                .configCount(configs.size())
                .configs(configs)
                .syncType("FULL")
                .source("MONITOR_SERVICE")
                .build();
    }

    public static ConfigSyncDTO incrementalSync(String configKey, String configValue) {
        return ConfigSyncDTO.builder()
                .version(generateVersion())
                .syncTime(LocalDateTime.now())
                .configCount(1)
                .configs(Map.of(configKey, configValue))
                .syncType("INCREMENTAL")
                .source("MONITOR_SERVICE")
                .build();
    }

    private static String generateVersion() {
        return String.valueOf(System.currentTimeMillis());
    }
}
