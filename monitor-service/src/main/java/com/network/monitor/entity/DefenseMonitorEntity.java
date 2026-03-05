package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 防御日志实体类
 * 对应数据库表：sys_defense_monitor
 */
@Data
public class DefenseMonitorEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 关联攻击 ID
     */
    private Long attackId;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 防御类型（IP 拉黑/请求限流/恶意请求拦截）
     */
    private String defenseType;

    /**
     * 防御动作（ADD/REMOVE/UPDATE）
     */
    private String defenseAction;

    /**
     * 防御对象（IP 地址/规则 ID）
     */
    private String defenseTarget;

    /**
     * 防御原因
     */
    private String defenseReason;

    /**
     * 防御过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 执行状态（0-失败，1-成功）
     */
    private Integer executeStatus;

    /**
     * 执行结果信息
     */
    private String executeResult;

    /**
     * 操作人（SYSTEM/MANUAL）
     */
    private String operator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
