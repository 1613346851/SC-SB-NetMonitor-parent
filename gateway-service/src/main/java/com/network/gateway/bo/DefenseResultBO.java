package com.network.gateway.bo;

import com.network.gateway.dto.DefenseLogDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 防御结果业务对象
 * 网关内部使用的防御执行结果封装对象
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefenseResultBO {

    /**
     * 防御是否成功执行
     */
    private Boolean success;

    /**
     * 防御类型
     */
    private DefenseType defenseType;

    /**
     * 目标IP
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
     * 响应状态码
     */
    private Integer responseStatusCode;

    /**
     * 响应内容
     */
    private String responseContent;

    /**
     * 执行开始时间戳
     */
    private Long executeStartTime;

    /**
     * 执行结束时间戳
     */
    private Long executeEndTime;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTime;

    /**
     * 错误信息（如果执行失败）
     */
    private String errorMessage;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求URI
     */
    private String uri;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 防御类型枚举
     */
    public enum DefenseType {
        /** IP黑名单 */
        BLACKLIST("IP黑名单防御"),
        /** 请求限流 */
        RATE_LIMIT("请求限流防御"),
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
        LOW,
        /** 中风险 */
        MEDIUM,
        /** 高风险 */
        HIGH,
        /** 严重风险 */
        CRITICAL
    }

    /**
     * 构造函数（用于创建成功的防御结果）
     *
     * @param defenseType 防御类型
     * @param targetIp 目标IP
     * @param eventId 事件ID
     * @param triggerReason 触发原因
     */
    public DefenseResultBO(DefenseType defenseType, String targetIp, String eventId, String triggerReason) {
        this.success = true;
        this.defenseType = defenseType;
        this.targetIp = targetIp;
        this.eventId = eventId;
        this.triggerReason = triggerReason;
        this.executeStartTime = System.currentTimeMillis();
    }

    /**
     * 构造函数（用于创建失败的防御结果）
     *
     * @param defenseType 防御类型
     * @param targetIp 目标IP
     * @param errorMessage 错误信息
     */
    public DefenseResultBO(DefenseType defenseType, String targetIp, String errorMessage) {
        this.success = false;
        this.defenseType = defenseType;
        this.targetIp = targetIp;
        this.errorMessage = errorMessage;
        this.executeStartTime = System.currentTimeMillis();
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = 0L;
    }

    /**
     * 设置执行成功信息
     *
     * @param responseStatusCode 响应状态码
     * @param responseContent 响应内容
     */
    public void setSuccessResult(Integer responseStatusCode, String responseContent) {
        this.success = true;
        this.responseStatusCode = responseStatusCode;
        this.responseContent = responseContent;
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = this.executeEndTime - this.executeStartTime;
    }

    /**
     * 设置执行失败信息
     *
     * @param errorMessage 错误信息
     */
    public void setFailureResult(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = this.executeEndTime - this.executeStartTime;
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
     * 设置风险等级
     *
     * @param riskLevel 风险等级
     */
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
     * 转换为防御日志DTO
     *
     * @return DefenseLogDTO
     */
    public DefenseLogDTO toDefenseLogDTO() {
        DefenseLogDTO logDTO = new DefenseLogDTO(
                convertDefenseType(),
                this.targetIp,
                this.eventId,
                this.triggerReason
        );

        logDTO.setResponseInfo(this.responseStatusCode, buildResultDescription());
        logDTO.setRequestInfo(this.method, this.uri, this.userAgent);
        logDTO.setProcessingTime(this.executeStartTime);
        logDTO.setRiskLevel(convertRiskLevel());

        if (!this.success) {
            logDTO.markAsFailed(this.errorMessage);
        }

        return logDTO;
    }

    /**
     * 转换防御类型为DTO枚举
     *
     * @return DTO中的防御类型
     */
    private DefenseLogDTO.DefenseType convertDefenseType() {
        switch (this.defenseType) {
            case BLACKLIST:
                return DefenseLogDTO.DefenseType.BLACKLIST;
            case RATE_LIMIT:
                return DefenseLogDTO.DefenseType.RATE_LIMIT;
            case BLOCK:
                return DefenseLogDTO.DefenseType.BLOCK;
            default:
                throw new IllegalArgumentException("未知的防御类型: " + this.defenseType);
        }
    }

    /**
     * 转换风险等级为DTO枚举
     *
     * @return DTO中的风险等级
     */
    private DefenseLogDTO.RiskLevel convertRiskLevel() {
        if (this.riskLevel == null) {
            return DefenseLogDTO.RiskLevel.MEDIUM;
        }
        
        switch (this.riskLevel) {
            case LOW:
                return DefenseLogDTO.RiskLevel.LOW;
            case MEDIUM:
                return DefenseLogDTO.RiskLevel.MEDIUM;
            case HIGH:
                return DefenseLogDTO.RiskLevel.HIGH;
            case CRITICAL:
                return DefenseLogDTO.RiskLevel.CRITICAL;
            default:
                return DefenseLogDTO.RiskLevel.MEDIUM;
        }
    }

    /**
     * 构建结果描述
     *
     * @return 结果描述
     */
    private String buildResultDescription() {
        if (!this.success) {
            return "防御执行失败: " + this.errorMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("成功拦截攻击请求，返回状态码: ").append(this.responseStatusCode);
        
        if (this.responseContent != null && this.responseContent.length() > 50) {
            sb.append("，响应内容已截断");
        } else if (this.responseContent != null) {
            sb.append("，响应内容: ").append(this.responseContent);
        }

        return sb.toString();
    }

    /**
     * 获取防御执行摘要
     *
     * @return 执行摘要
     */
    public String getExecutionSummary() {
        return String.format("%s %s 针对IP[%s] 事件[%s] %s 耗时[%dms]",
                this.defenseType.getDescription(),
                this.success ? "成功" : "失败",
                this.targetIp,
                this.eventId,
                this.success ? "已拦截攻击" : "执行失败:" + this.errorMessage,
                this.processingTime);
    }

    /**
     * 检查是否为高风险防御
     *
     * @return true表示高风险
     */
    public boolean isHighRisk() {
        return this.riskLevel == RiskLevel.HIGH || this.riskLevel == RiskLevel.CRITICAL;
    }
}