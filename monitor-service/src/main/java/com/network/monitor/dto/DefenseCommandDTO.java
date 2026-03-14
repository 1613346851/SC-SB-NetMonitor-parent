package com.network.monitor.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 防御指令数据传输对象
 * 与 gateway-service 的 DefenseCommandDTO 保持一致
 */
@Data
public class DefenseCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 指令唯一标识
     */
    private String commandId;

    /**
     * 源IP地址（需要防御的目标IP）
     */
    private String sourceIp;

    /**
     * 防御类型
     * BLACKLIST: IP黑名单
     * RATE_LIMIT: 请求限流
     * BLOCK: 恶意请求拦截
     */
    private String defenseType;

    /**
     * 防御过期时间戳（毫秒）
     */
    private Long expireTimestamp;

    /**
     * 监测事件ID（关联的攻击事件）
     */
    private String eventId;

    /**
     * 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
     */
    private String riskLevel;

    /**
     * 指令描述
     */
    private String description;

    /**
     * 指令下发时间戳
     */
    private Long issueTimestamp;

    /**
     * 限流阈值（仅当defenseType为RATE_LIMIT时有效）
     */
    private Integer rateLimitThreshold;

    public DefenseCommandDTO() {
        this.commandId = generateCommandId();
        this.issueTimestamp = System.currentTimeMillis();
    }

    public DefenseCommandDTO(String sourceIp, String defenseType, Long expireTimestamp, String eventId, String riskLevel) {
        this.commandId = generateCommandId();
        this.sourceIp = sourceIp;
        this.defenseType = defenseType;
        this.expireTimestamp = expireTimestamp;
        this.eventId = eventId;
        this.riskLevel = riskLevel;
        this.issueTimestamp = System.currentTimeMillis();
        this.description = buildDescription(defenseType, riskLevel);
    }

    private String generateCommandId() {
        return "cmd_" + System.currentTimeMillis() + "_" + 
               String.valueOf((int)(Math.random() * 10000));
    }

    private String buildDescription(String defenseType, String riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("针对IP[").append(this.sourceIp).append("]的");
        
        if ("CRITICAL".equals(riskLevel)) {
            sb.append("严重");
        } else if ("HIGH".equals(riskLevel)) {
            sb.append("高");
        } else if ("MEDIUM".equals(riskLevel)) {
            sb.append("中");
        } else {
            sb.append("低");
        }
        
        if ("BLACKLIST".equals(defenseType)) {
            sb.append("风险IP黑名单防御");
        } else if ("RATE_LIMIT".equals(defenseType)) {
            sb.append("风险请求限流防御");
        } else if ("BLOCK".equals(defenseType)) {
            sb.append("恶意请求拦截防御");
        }
        
        return sb.toString();
    }

    public boolean isValid() {
        return this.sourceIp != null && !this.sourceIp.isEmpty() && !isExpired();
    }

    public boolean isExpired() {
        return this.expireTimestamp != null && System.currentTimeMillis() > this.expireTimestamp;
    }

    public long getRemainingTime() {
        if (this.expireTimestamp == null) {
            return Long.MAX_VALUE;
        }
        long remaining = this.expireTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
