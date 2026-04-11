package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描接口配置实体类
 * 对应数据库表：sys_scan_interface
 */
@Data
public class ScanInterfaceEntity {

    /**
     * 接口ID
     */
    private Long id;

    /**
     * 关联目标ID
     */
    private Long targetId;

    /**
     * 接口名称
     */
    private String interfaceName;

    /**
     * 接口路径
     */
    private String interfacePath;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 漏洞类型
     */
    private String vulnType;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 参数配置（JSON）
     */
    private String paramsConfig;

    /**
     * Payload配置（JSON）
     */
    private String payloadConfig;

    /**
     * 匹配规则（JSON）
     */
    private String matchRules;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled = 1;

    /**
     * 扫描优先级
     */
    private Integer priority = 100;

    /**
     * 防御规则状态（0-未配置，1-部分已配置，2-已配置）
     */
    private Integer defenseRuleStatus = 0;

    /**
     * 关联防御规则数量
     */
    private Integer defenseRuleCount = 0;

    /**
     * 防御规则说明
     */
    private String defenseRuleNote;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
