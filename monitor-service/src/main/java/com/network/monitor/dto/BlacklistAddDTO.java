package com.network.monitor.dto;

import lombok.Data;

@Data
public class BlacklistAddDTO {
    private String ipAddress;
    private String reason;
    private Integer expireSeconds;
    private String operator;
}