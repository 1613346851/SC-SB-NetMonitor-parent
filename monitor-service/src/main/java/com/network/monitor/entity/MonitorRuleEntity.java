package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 攻击规则实体类
 * 对应数据库表：sys_monitor_rule
 */
@Data
public class MonitorRuleEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 攻击类型（SQL 注入/XSS/命令注入/DDoS 等）
     */
    private String attackType;

    /**
     * 规则内容（正则表达式/关键词）
     */
    private String ruleContent;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 风险等级（HIGH/MEDIUM/LOW）
     */
    private String riskLevel;

    /**
     * 启用状态（0-禁用，1-启用）
     */
    private Integer enabled = 1;

    /**
     * 规则优先级（数字越小优先级越高）
     */
    private Integer priority = 100;

    /**
     * 命中次数统计
     */
    private Integer hitCount = 0;

    /**
     * 最后命中时间
     */
    private LocalDateTime lastHitTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
