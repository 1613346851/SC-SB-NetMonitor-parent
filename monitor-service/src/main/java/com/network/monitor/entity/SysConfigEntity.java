package com.network.monitor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysConfigEntity {

    private Long id;

    private String configKey;

    private String configValue;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}