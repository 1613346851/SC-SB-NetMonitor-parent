package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 规则同步数据传输对象
 * 用于监测服务向网关服务推送规则变更
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleSyncDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 攻击类型
     * SQL_INJECTION: SQL注入
     * XSS: XSS攻击
     * COMMAND_INJECTION: 命令注入
     * PATH_TRAVERSAL: 路径遍历
     * FILE_INCLUSION: 文件包含
     * DDoS: DDoS攻击
     */
    private String attackType;

    /**
     * 规则内容（正则表达式）
     */
    private String ruleContent;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 风险等级
     * CRITICAL: 严重
     * HIGH: 高风险
     * MEDIUM: 中风险
     * LOW: 低风险
     */
    private String riskLevel;

    /**
     * 启用状态
     * 0: 禁用
     * 1: 启用
     */
    private Integer enabled;

    /**
     * 规则优先级（数字越小优先级越高）
     */
    private Integer priority;

    /**
     * 同步时间戳
     */
    private Long timestamp;

    /**
     * 操作类型
     * ADD: 新增
     * UPDATE: 更新
     * DELETE: 删除
     */
    private String operation;

    public enum Operation {
        ADD,
        UPDATE,
        DELETE
    }
}
