package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 攻击监测实体类
 * 对应数据库表：sys_attack_monitor
 */
@Data
public class AttackMonitorEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 攻击类型
     */
    private String attackType;

    /**
     * 风险等级（HIGH/MEDIUM/LOW）
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

    /**
     * 是否已处理（0-未处理，1-已处理）
     */
    private Integer handled;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // ==================== 关联字段（非数据库字段） ====================

    /**
     * 关联的流量信息
     */
    private TrafficMonitorEntity traffic;

    /**
     * 关联的防御日志列表
     */
    private java.util.List<DefenseLogEntity> defenseLogs;

    // ==================== 辅助方法 ====================

    /**
     * 判断是否为高危攻击
     */
    public boolean isHighRisk() {
        return "HIGH".equals(this.riskLevel);
    }

    /**
     * 判断是否为中危攻击
     */
    public boolean isMediumRisk() {
        return "MEDIUM".equals(this.riskLevel);
    }

    /**
     * 判断是否为低危攻击
     */
    public boolean isLowRisk() {
        return "LOW".equals(this.riskLevel);
    }

    /**
     * 判断是否已处理
     */
    public boolean isHandled() {
        return this.handled != null && this.handled == 1;
    }

    /**
     * 标记为已处理
     */
    public void markAsHandled(String remark) {
        this.handled = 1;
        this.handleTime = LocalDateTime.now();
        this.handleRemark = remark;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 获取攻击类型中文名称
     */
    public String getAttackTypeChinese() {
        if (this.attackType == null) {
            return "未知";
        }
        switch (this.attackType) {
            case "SQL_INJECTION":
                return "SQL 注入";
            case "XSS":
                return "跨站脚本";
            case "COMMAND_INJECTION":
                return "命令注入";
            case "PATH_TRAVERSAL":
                return "路径遍历";
            case "FILE_INCLUSION":
                return "文件包含";
            case "DDOS":
                return "DDoS 攻击";
            case "BRUTE_FORCE":
                return "暴力破解";
            case "SCANNER":
                return "扫描器探测";
            default:
                return this.attackType;
        }
    }

    /**
     * 获取风险等级中文名称
     */
    public String getRiskLevelChinese() {
        if (this.riskLevel == null) {
            return "未知";
        }
        switch (this.riskLevel) {
            case "HIGH":
                return "高危";
            case "MEDIUM":
                return "中危";
            case "LOW":
                return "低危";
            default:
                return this.riskLevel;
        }
    }
}
