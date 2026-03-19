package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DefenseLogEntity {

    private Long id;

    private String defenseType;

    private String defenseAction;

    private String defenseTarget;

    private Long attackId;

    private Long trafficId;

    private Long ruleId;

    private String defenseReason;

    private LocalDateTime expireTime;

    private Integer executeStatus;

    private String executeResult;

    private String operator;

    private LocalDateTime executeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
