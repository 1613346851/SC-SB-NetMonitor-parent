package com.network.monitor.dto;

import lombok.Data;

/**
 * 攻击监测数据传输对象
 */
@Data
public class AttackMonitorDTO {

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 攻击类型
     */
    private String attackType;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 攻击置信度（0-100）
     */
    private Integer confidence;

    /**
     * 命中规则 ID
     */
    private Long ruleId;

    /**
     * 命中规则内容
     */
    private String ruleContent;

    /**
     * 源 IP 地址
     */
    private String sourceIp;

    /**
     * 目标 URI
     */
    private String targetUri;

    /**
     * 攻击内容（解码后）
     */
    private String attackContent;
}
