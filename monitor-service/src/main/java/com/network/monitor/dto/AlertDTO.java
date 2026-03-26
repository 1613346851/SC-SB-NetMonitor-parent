package com.network.monitor.dto;

import lombok.Data;

/**
 * 告警数据传输对象
 */
@Data
public class AlertDTO {

    private String eventId;

    private String sourceIp;

    private String attackType;

    private String alertLevel;

    private String alertTitle;

    private String alertContent;

    private String notifyChannels;

    public static AlertDTO fromAttack(String eventId, String sourceIp, String attackType, String riskLevel) {
        AlertDTO dto = new AlertDTO();
        dto.setEventId(eventId);
        dto.setSourceIp(sourceIp);
        dto.setAttackType(attackType);
        dto.setAlertLevel(mapRiskToAlertLevel(riskLevel));
        dto.setAlertTitle(buildAlertTitle(attackType, sourceIp));
        dto.setAlertContent(buildAlertContent(attackType, sourceIp, riskLevel));
        return dto;
    }

    private static String mapRiskToAlertLevel(String riskLevel) {
        if (riskLevel == null) {
            return "MEDIUM";
        }
        switch (riskLevel) {
            case "CRITICAL":
                return "CRITICAL";
            case "HIGH":
                return "HIGH";
            case "MEDIUM":
                return "MEDIUM";
            case "LOW":
                return "LOW";
            default:
                return "MEDIUM";
        }
    }

    private static String buildAlertTitle(String attackType, String sourceIp) {
        String attackTypeName = getAttackTypeName(attackType);
        return String.format("检测到%s攻击 - 来源IP: %s", attackTypeName, sourceIp);
    }

    private static String buildAlertContent(String attackType, String sourceIp, String riskLevel) {
        String attackTypeName = getAttackTypeName(attackType);
        return String.format("检测到%s攻击\n来源IP: %s\n风险等级: %s\n请及时处理。", 
            attackTypeName, sourceIp, riskLevel);
    }

    private static String getAttackTypeName(String attackType) {
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
                return "DDoS";
            case "BRUTE_FORCE":
                return "暴力破解";
            case "SCANNER":
                return "扫描器探测";
            default:
                return attackType;
        }
    }
}
