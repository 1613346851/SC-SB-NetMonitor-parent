package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口-规则关联实体类
 * 对应数据库表：sys_interface_rule
 */
@Data
public class InterfaceRuleEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 接口ID
     */
    private Long interfaceId;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称（冗余字段）
     */
    private String ruleName;

    /**
     * 攻击类型（冗余字段）
     */
    private String attackType;

    /**
     * 风险等级（冗余字段）
     */
    private String riskLevel;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
