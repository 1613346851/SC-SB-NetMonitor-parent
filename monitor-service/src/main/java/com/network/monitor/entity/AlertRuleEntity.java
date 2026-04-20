package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则实体类
 * 对应数据库表：sys_alert_rule
 */
@Data
public class AlertRuleEntity {

    private Long id;

    private String ruleName;

    private String ruleCode;

    private String attackType;

    private String riskLevel;

    private String alertLevel;

    private Integer thresholdCount;

    private Integer thresholdWindowSeconds;

    private Integer suppressDurationSeconds;

    private Integer enabled;

    private Integer priority;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public boolean isEnabled() {
        return this.enabled != null && this.enabled == 1;
    }

    public String getAlertLevelChinese() {
        if (this.alertLevel == null) {
            return "未知";
        }
        switch (this.alertLevel) {
            case "CRITICAL":
                return "严重";
            case "HIGH":
                return "高危";
            case "MEDIUM":
                return "中危";
            case "LOW":
                return "低危";
            default:
                return this.alertLevel;
        }
    }
}
