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
     */
    private DefenseType defenseType;

    /**
     * 防御过期时间戳（毫秒）
     */
    private Long expireTimestamp;

    /**
     * 监测事件ID（关联的攻击事件）
     */
    private String eventId;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

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

    /**
     * 防御类型枚举
     */
    public enum DefenseType {
        /** IP黑名单 */
        BLACKLIST,
        /** 请求限流 */
        RATE_LIMIT,
        /** 恶意请求拦截 */
        BLOCK
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        /** 低风险 */
        LOW,
        /** 中风险 */
        MEDIUM,
        /** 高风险 */
        HIGH,
        /** 严重风险 */
        CRITICAL
    }

    public DefenseCommandDTO() {
        this.commandId = generateCommandId();
        this.issueTimestamp = System.currentTimeMillis();
    }

    public DefenseCommandDTO(String sourceIp, DefenseType defenseType, Long expireTimestamp, 
                            String eventId, RiskLevel riskLevel) {
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

    private String buildDescription(DefenseType defenseType, RiskLevel riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("针对IP[").append(this.sourceIp).append("]的");
        
        if (riskLevel != null) {
            switch (riskLevel) {
                case CRITICAL -> sb.append("严重");
                case HIGH -> sb.append("高");
                case MEDIUM -> sb.append("中");
                case LOW -> sb.append("低");
            }
        }
        
        if (defenseType != null) {
            switch (defenseType) {
                case BLACKLIST -> sb.append("风险IP黑名单防御");
                case RATE_LIMIT -> sb.append("风险请求限流防御");
                case BLOCK -> sb.append("恶意请求拦截防御");
            }
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
