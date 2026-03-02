package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 防御日志DTO
 * 网关向监控服务推送防御执行日志的数据传输对象
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefenseLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志唯一标识
     */
    private String logId;

    /**
     * 防御类型
     */
    private DefenseType defenseType;

    /**
     * 目标IP地址
     */
    private String targetIp;

    /**
     * 监测事件ID
     */
    private String eventId;

    /**
     * 触发原因
     */
    private String triggerReason;

    /**
     * 防御执行时间戳
     */
    private Long executeTimestamp;

    /**
     * 响应状态码
     */
    private Integer responseStatusCode;

    /**
     * 防御结果描述
     */
    private String resultDescription;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTime;

    /**
     * 是否成功执行
     */
    private Boolean success;

    /**
     * 错误信息（如果执行失败）
     */
    private String errorMessage;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求URI
     */
    private String uri;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 防御类型枚举
     */
    public enum DefenseType {
        /** IP黑名单 */
        BLACKLIST("IP黑名单"),
        /** 请求限流 */
        RATE_LIMIT("请求限流"),
        /** 恶意请求拦截 */
        BLOCK("恶意请求拦截");
        
        private final String description;
        
        DefenseType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        /** 低风险 */
        LOW("低风险"),
        /** 中风险 */
        MEDIUM("中风险"),
        /** 高风险 */
        HIGH("高风险"),
        /** 严重风险 */
        CRITICAL("严重风险");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * 构造函数（用于创建防御日志）
     *
     * @param defenseType 防御类型
     * @param targetIp 目标IP
     * @param eventId 事件ID
     * @param triggerReason 触发原因
     */
    public DefenseLogDTO(DefenseType defenseType, String targetIp, String eventId, String triggerReason) {
        this.logId = generateLogId();
        this.defenseType = defenseType;
        this.targetIp = targetIp;
        this.eventId = eventId;
        this.triggerReason = triggerReason;
        this.executeTimestamp = System.currentTimeMillis();
        this.success = true; // 默认成功
        this.processingTime = 0L;
    }

    /**
     * 设置响应信息
     *
     * @param responseStatusCode 响应状态码
     * @param resultDescription 结果描述
     */
    public void setResponseInfo(Integer responseStatusCode, String resultDescription) {
        this.responseStatusCode = responseStatusCode;
        this.resultDescription = resultDescription;
    }

    /**
     * 标记执行失败
     *
     * @param errorMessage 错误信息
     */
    public void markAsFailed(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    /**
     * 设置请求信息
     *
     * @param method 请求方法
     * @param uri 请求URI
     * @param userAgent 用户代理
     */
    public void setRequestInfo(String method, String uri, String userAgent) {
        this.method = method;
        this.uri = uri;
        this.userAgent = userAgent;
    }

    /**
     * 设置处理耗时
     *
     * @param startTime 开始时间戳
     */
    public void setProcessingTime(Long startTime) {
        this.processingTime = System.currentTimeMillis() - startTime;
    }

    /**
     * 设置风险等级
     *
     * @param riskLevel 风险等级
     */
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
     * 生成日志ID
     *
     * @return 日志ID
     */
    private String generateLogId() {
        return "log_" + System.currentTimeMillis() + "_" + 
               String.valueOf((int)(Math.random() * 10000));
    }

    /**
     * 构建详细的日志描述
     *
     * @return 详细描述
     */
    public String buildDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("执行").append(this.defenseType.getDescription()).append(": ");
        sb.append("IP[").append(this.targetIp).append("] ");
        sb.append("事件[").append(this.eventId).append("] ");
        sb.append("原因[").append(this.triggerReason).append("] ");
        sb.append("结果[").append(this.resultDescription).append("]");
        
        if (!this.success) {
            sb.append(" 错误:[").append(this.errorMessage).append("]");
        }
        
        return sb.toString();
    }
}