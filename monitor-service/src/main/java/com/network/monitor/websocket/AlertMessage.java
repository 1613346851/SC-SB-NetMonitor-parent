package com.network.monitor.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {

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

    private LocalDateTime createTime;

    public String getAlertLevelClass() {
        if (alertLevel == null) {
            return "alert-low";
        }
        switch (alertLevel) {
            case "CRITICAL":
                return "alert-critical";
            case "HIGH":
                return "alert-high";
            case "MEDIUM":
                return "alert-medium";
            case "LOW":
            default:
                return "alert-low";
        }
    }

    public String getAlertLevelChinese() {
        if (alertLevel == null) {
            return "低危";
        }
        switch (alertLevel) {
            case "CRITICAL":
                return "严重";
            case "HIGH":
                return "高危";
            case "MEDIUM":
                return "中危";
            case "LOW":
            default:
                return "低危";
        }
    }

    public String getAttackTypeChinese() {
        if (attackType == null) {
            return "未知";
        }
        switch (attackType) {
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
                return attackType;
        }
    }
}
