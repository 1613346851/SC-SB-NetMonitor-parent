package com.network.monitor.dto;

import lombok.Data;

/**
 * 防御日志数据传输对象
 */
@Data
public class DefenseLogDTO {

    private String eventId;

    private Long attackId;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 防御类型
     */
    private String defenseType;

    /**
     * 防御动作
     */
    private String defenseAction;

    /**
     * 防御对象
     */
    private String defenseTarget;

    /**
     * 防御原因
     */
    private String defenseReason;

    /**
     * 防御过期时间
     */
    private String expireTime;

    /**
     * 执行状态（0-失败，1-成功）
     */
    private Integer executeStatus;

    /**
     * 执行结果信息
     */
    private String executeResult;

    /**
     * 操作人
     */
    private String operator;
}
