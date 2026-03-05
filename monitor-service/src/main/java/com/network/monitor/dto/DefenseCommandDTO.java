package com.network.monitor.dto;

import lombok.Data;

/**
 * 防御指令数据传输对象
 */
@Data
public class DefenseCommandDTO {

    /**
     * 关联攻击 ID
     */
    private Long attackId;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 防御类型（IP_BLOCK/RATE_LIMIT/MALICIOUS_BLOCK）
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
    private String expireTime;

    /**
     * 风险等级
     */
    private String riskLevel;
}
