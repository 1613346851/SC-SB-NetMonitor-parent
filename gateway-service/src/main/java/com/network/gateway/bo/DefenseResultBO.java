package com.network.gateway.bo;

import com.network.gateway.dto.DefenseLogDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    private Boolean success;

    private DefenseType defenseType;

    private String targetIp;

    private String eventId;

    private String triggerReason;

    private Integer responseStatusCode;

    private String responseContent;

    private Long executeStartTime;

    private Long executeEndTime;

    private Long processingTime;

    private String errorMessage;

    private RiskLevel riskLevel;

    private String method;

    private String uri;

    private String userAgent;

    private Long expireTimestamp;

    public enum DefenseType {
        BLACKLIST("IP黑名单防御", "BLOCK_IP"),
        RATE_LIMIT("请求限流防御", "RATE_LIMIT"),
        BLOCK("恶意请求拦截", "BLOCK_REQUEST");

        private final String description;
        private final String code;

        DefenseType(String description, String code) {
            this.description = description;
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public String getCode() {
            return code;
        }
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public DefenseResultBO(DefenseType defenseType, String targetIp, String eventId, String triggerReason) {
        this.success = true;
        this.defenseType = defenseType;
        this.targetIp = targetIp;
        this.eventId = eventId;
        this.triggerReason = triggerReason;
        this.executeStartTime = System.currentTimeMillis();
    }

    public DefenseResultBO(DefenseType defenseType, String targetIp, String errorMessage) {
        this.success = false;
        this.defenseType = defenseType;
        this.targetIp = targetIp;
        this.errorMessage = errorMessage;
        this.executeStartTime = System.currentTimeMillis();
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = 0L;
    }

    public void setSuccessResult(Integer responseStatusCode, String responseContent) {
        this.success = true;
        this.responseStatusCode = responseStatusCode;
        this.responseContent = responseContent;
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = this.executeEndTime - this.executeStartTime;
    }

    public void setFailureResult(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
        this.executeEndTime = System.currentTimeMillis();
        this.processingTime = this.executeEndTime - this.executeStartTime;
    }

    public void setRequestInfo(String method, String uri, String userAgent) {
        this.method = method;
        this.uri = uri;
        this.userAgent = userAgent;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public DefenseLogDTO toDefenseLogDTO() {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        
        logDTO.setDefenseType(this.defenseType.getCode());
        logDTO.setDefenseTarget(this.targetIp);
        logDTO.setDefenseReason(this.triggerReason);
        logDTO.setDefenseAction(getDefenseAction());
        logDTO.setExecuteStatus(this.success ? 1 : 0);
        logDTO.setExecuteResult(buildResultDescription());
        logDTO.setOperator("SYSTEM");
        
        if (this.expireTimestamp != null) {
            LocalDateTime expireDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(this.expireTimestamp), 
                ZoneId.systemDefault()
            );
            logDTO.setExpireTime(expireDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        
        return logDTO;
    }

    private String getDefenseAction() {
        return switch (this.defenseType) {
            case BLACKLIST -> "ADD_BLACKLIST";
            case RATE_LIMIT -> "RATE_LIMIT";
            case BLOCK -> "BLOCK_REQUEST";
        };
    }

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

    public String getExecutionSummary() {
        return String.format("%s %s 针对IP[%s] 事件[%s] %s 耗时[%dms]",
                this.defenseType.getDescription(),
                this.success ? "成功" : "失败",
                this.targetIp,
                this.eventId,
                this.success ? "已拦截攻击" : "执行失败:" + this.errorMessage,
                this.processingTime);
    }

    public boolean isHighRisk() {
        return this.riskLevel == RiskLevel.HIGH || this.riskLevel == RiskLevel.CRITICAL;
    }
}
