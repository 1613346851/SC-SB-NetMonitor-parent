package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class StateTransitionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int fromState;
    private int toState;
    private long transitionTime;
    private String reason;
    private int confidence;
    private String operator;
    private String resetReason;
    private String traceId;

    public StateTransitionDTO() {
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String getFromStateName() {
        return getStateName(fromState);
    }

    public String getToStateName() {
        return getStateName(toState);
    }

    private String getStateName(int state) {
        switch (state) {
            case 0: return "正常";
            case 1: return "可疑";
            case 2: return "攻击中";
            case 3: return "已防御";
            case 4: return "冷却期";
            case 5: return "人工重置";
            default: return "未知";
        }
    }
}
