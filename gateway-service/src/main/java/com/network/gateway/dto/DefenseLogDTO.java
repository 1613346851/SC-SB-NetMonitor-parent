package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 防御日志DTO
 * 网关向监控服务推送防御执行日志的数据传输对象
 * 字段与监控服务 DefenseLogDTO 保持一致
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
     * 关联攻击 ID
     */
    private Long attackId;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 防御类型
     */
    private String defenseType;

    /**
     * 防御动作
     */
    private String defenseAction;

    /**
     * 防御对象（IP地址）
     */
    private String defenseTarget;

    /**
     * 防御原因
     */
    private String defenseReason;

    /**
     * 防御过期时间（字符串格式）
     */
    private String expireTime;

    /**
     * 执行状态（0-失败，1-成功）
     */
    private Integer executeStatus;

    /**
     * 执行结果信息
     */
    private String executeResult;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 构造函数（用于创建防御日志）
     *
     * @param defenseType 防御类型
     * @param defenseTarget 防御对象
     * @param defenseReason 防御原因
     */
    public DefenseLogDTO(String defenseType, String defenseTarget, String defenseReason) {
        this.defenseType = defenseType;
        this.defenseTarget = defenseTarget;
        this.defenseReason = defenseReason;
        this.executeStatus = 1;
        this.operator = "SYSTEM";
    }

    /**
     * 设置执行结果
     *
     * @param success 是否成功
     * @param executeResult 执行结果描述
     */
    public void setExecutionResult(boolean success, String executeResult) {
        this.executeStatus = success ? 1 : 0;
        this.executeResult = executeResult;
    }

    /**
     * 设置过期时间
     *
     * @param expireTimestamp 过期时间戳（毫秒）
     */
    public void setExpireTimestamp(Long expireTimestamp) {
        if (expireTimestamp != null) {
            this.expireTime = String.valueOf(expireTimestamp);
        }
    }
}
