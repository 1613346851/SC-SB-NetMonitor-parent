package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警实体类
 * 对应数据库表：sys_alert
 */
@Data
public class AlertEntity {

    private Long id;

    private String alertId;

    private String eventId;

    private Long attackId;

    private String sourceIp;

    private String attackType;

    private String alertLevel;

    private String alertTitle;

    private String alertContent;

    private Integer status;

    private Integer isSuppressed;

    private LocalDateTime suppressUntil;

    private String confirmBy;

    private LocalDateTime confirmTime;

    private String ignoreReason;

    private String ignoreBy;

    private LocalDateTime ignoreTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public boolean isPending() {
        return this.status != null && this.status == 0;
    }

    public boolean isConfirmed() {
        return this.status != null && this.status == 1;
    }

    public boolean isIgnored() {
        return this.status != null && this.status == 2;
    }

    public boolean isSuppressedNow() {
        if (this.isSuppressed == null || this.isSuppressed == 0) {
            return false;
        }
        if (this.suppressUntil == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(this.suppressUntil);
    }

    public String getAlertLevelChinese() {
        if (this.alertLevel == null) {
            return "未知";
        }
        switch (this.alertLevel) {
            case "CRITICAL":
                return "严重";
            case "HIGH":
                return "高风险";
            case "MEDIUM":
                return "中风险";
            case "LOW":
                return "低风险";
            default:
                return this.alertLevel;
        }
    }

    public String getStatusChinese() {
        if (this.status == null) {
            return "未知";
        }
        switch (this.status) {
            case 0:
                return "待处理";
            case 1:
                return "已确认";
            case 2:
                return "已忽略";
            default:
                return "未知";
        }
    }

    public String getAttackTypeChinese() {
        if (this.attackType == null) {
            return "未知";
        }
        switch (this.attackType) {
            case "SQL_INJECTION":
                return "SQL注入";
            case "XSS":
                return "跨站脚本";
            case "COMMAND_INJECTION":
                return "命令注入";
            case "PATH_TRAVERSAL":
                return "路径遍历";
            case "FILE_INCLUSION":
                return "文件包含";
            case "DDOS":
                return "DDoS攻击";
            case "BRUTE_FORCE":
                return "暴力破解";
            case "SCANNER":
                return "扫描器探测";
            default:
                return this.attackType;
        }
    }
}
