package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 防御指令DTO
 * 监控服务向网关推送防御指令的数据传输对象
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
     * 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
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

    /**
     * 构造函数（用于创建防御指令）
     *
     * @param sourceIp 源IP
     * @param defenseType 防御类型
     * @param expireTimestamp 过期时间戳
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     */
    public DefenseCommandDTO(String sourceIp, DefenseType defenseType, 
                           Long expireTimestamp, String eventId, RiskLevel riskLevel) {
        this.commandId = generateCommandId();
        this.sourceIp = sourceIp;
        this.defenseType = defenseType;
        this.expireTimestamp = expireTimestamp;
        this.eventId = eventId;
        this.riskLevel = riskLevel;
        this.issueTimestamp = System.currentTimeMillis();
        this.description = buildDescription(defenseType, riskLevel);
    }

    /**
     * 检查指令是否已过期
     * 如果 expireTimestamp 为 null，表示永久有效，不会过期
     *
     * @return true表示已过期
     */
    public boolean isExpired() {
        return this.expireTimestamp != null && System.currentTimeMillis() > this.expireTimestamp;
    }

    /**
     * 检查指令是否有效（未过期且IP不为空）
     *
     * @return true表示有效
     */
    public boolean isValid() {
        return this.sourceIp != null && !this.sourceIp.isEmpty() && !isExpired();
    }

    /**
     * 生成指令ID
     *
     * @return 指令ID
     */
    private String generateCommandId() {
        return "cmd_" + System.currentTimeMillis() + "_" + 
               String.valueOf((int)(Math.random() * 10000));
    }

    /**
     * 构建指令描述
     *
     * @param defenseType 防御类型
     * @param riskLevel 风险等级
     * @return 描述信息
     */
    private String buildDescription(DefenseType defenseType, RiskLevel riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("针对IP[").append(this.sourceIp).append("]的");
        
        switch (riskLevel) {
            case CRITICAL:
                sb.append("严重");
                break;
            case HIGH:
                sb.append("高");
                break;
            case MEDIUM:
                sb.append("中");
                break;
            case LOW:
                sb.append("低");
                break;
        }
        
        switch (defenseType) {
            case BLACKLIST:
                sb.append("风险IP黑名单防御");
                break;
            case RATE_LIMIT:
                sb.append("风险请求限流防御");
                break;
            case BLOCK:
                sb.append("恶意请求拦截防御");
                break;
        }
        
        return sb.toString();
    }

    /**
     * 获取剩余有效时间（毫秒）
     *
     * @return 剩余时间
     */
    public long getRemainingTime() {
        long remaining = this.expireTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}