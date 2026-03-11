package com.network.monitor.dto;

import lombok.Data;

@Data
public class BlacklistInfoDTO {
    private Long id;
    private String ip;
    private String reason;
    private String expireTime;
    private String createTime;
    private String operator;
    private Integer status;
}
