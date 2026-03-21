package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttackEventEntity {

    private Long id;

    private String eventId;

    private String sourceIp;

    private String attackType;

    private String riskLevel;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private Integer totalRequests;

    private Integer peakRps;

    private Integer attackCount;

    private Integer confidenceStart;

    private Integer confidenceEnd;

    private String defenseAction;

    private LocalDateTime defenseExpireTime;

    private Integer defenseSuccess;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final int STATUS_ONGOING = 0;
    public static final int STATUS_ENDED = 1;

    public boolean isOngoing() {
        return this.status != null && this.status == STATUS_ONGOING;
    }

    public boolean isEnded() {
        return this.status != null && this.status == STATUS_ENDED;
    }

    public void markAsEnded() {
        this.status = STATUS_ENDED;
        this.endTime = LocalDateTime.now();
        if (this.startTime != null) {
            this.durationSeconds = (int) java.time.Duration.between(this.startTime, this.endTime).getSeconds();
        }
        this.updateTime = LocalDateTime.now();
    }

    public void incrementAttackCount() {
        if (this.attackCount == null) {
            this.attackCount = 0;
        }
        this.attackCount++;
    }

    public void addRequests(int count) {
        if (this.totalRequests == null) {
            this.totalRequests = 0;
        }
        this.totalRequests += count;
    }

    public void updatePeakRps(int currentRps) {
        if (this.peakRps == null || currentRps > this.peakRps) {
            this.peakRps = currentRps;
        }
    }

    public void updateConfidence(int confidence) {
        if (this.confidenceStart == null) {
            this.confidenceStart = confidence;
        }
        this.confidenceEnd = confidence;
    }

    public String getAttackTypeChinese() {
        if (this.attackType == null) {
            return "未知";
        }
        return switch (this.attackType.toUpperCase()) {
            case "SQL_INJECTION" -> "SQL 注入";
            case "XSS" -> "跨站脚本";
            case "COMMAND_INJECTION" -> "命令注入";
            case "PATH_TRAVERSAL" -> "路径遍历";
            case "FILE_INCLUSION" -> "文件包含";
            case "DDOS" -> "DDoS 攻击";
            case "BRUTE_FORCE" -> "暴力破解";
            case "SCANNER" -> "扫描器探测";
            case "RATE_LIMIT" -> "频率限制";
            default -> this.attackType;
        };
    }

    public String getRiskLevelChinese() {
        if (this.riskLevel == null) {
            return "未知";
        }
        return switch (this.riskLevel.toUpperCase()) {
            case "CRITICAL" -> "严重";
            case "HIGH" -> "高危";
            case "MEDIUM" -> "中危";
            case "LOW" -> "低危";
            default -> this.riskLevel;
        };
    }

    public String getStatusChinese() {
        if (this.status == null) {
            return "未知";
        }
        return switch (this.status) {
            case STATUS_ONGOING -> "进行中";
            case STATUS_ENDED -> "已结束";
            default -> "未知";
        };
    }

    public String getDefenseActionChinese() {
        if (this.defenseAction == null) {
            return "无";
        }
        return switch (this.defenseAction.toUpperCase()) {
            case "BLACKLIST" -> "IP黑名单";
            case "RATE_LIMIT" -> "请求限流";
            case "BLOCK" -> "请求拦截";
            default -> this.defenseAction;
        };
    }
}
