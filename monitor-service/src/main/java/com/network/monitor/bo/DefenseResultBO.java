package com.network.monitor.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 防御结果业务对象
 * 用于封装防御操作的执行结果
 */
@Data
public class DefenseResultBO {

    /**
     * 防御事件 ID
     */
    private Long defenseId;

    /**
     * 关联攻击 ID
     */
    private Long attackId;

    /**
     * 关联流量 ID
     */
    private Long trafficId;

    /**
     * 防御类型（IP 拉黑、限流、拦截等）
     */
    private String defenseType;

    /**
     * 防御动作（ADD/REMOVE/UPDATE）
     */
    private String defenseAction;

    /**
     * 防御目标（IP 地址等）
     */
    private String defenseTarget;

    /**
     * 防御原因
     */
    private String defenseReason;

    /**
     * 执行状态（SUCCESS/FAILED/PENDING）
     */
    private String executeStatus;

    /**
     * 执行结果消息
     */
    private String executeMessage;

    /**
     * 过期时间
     */
    private String expireTime;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 标记为成功
     */
    public void markSuccess() {
        this.success = true;
        this.executeStatus = "SUCCESS";
        this.executeTime = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markFailed(String message) {
        this.success = false;
        this.executeStatus = "FAILED";
        this.executeMessage = message;
        this.executeTime = LocalDateTime.now();
    }

    /**
     * 标记为执行中
     */
    public void markPending() {
        this.success = false;
        this.executeStatus = "PENDING";
    }
}
