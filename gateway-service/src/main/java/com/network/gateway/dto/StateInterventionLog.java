package com.network.gateway.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class StateInterventionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int fromState;
    private int toState;
    private String interventionType;
    private String operator;
    private String reason;
    private long timestamp;

    public StateInterventionLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public StateInterventionLog(String ip, int fromState, int toState, 
                                String interventionType, String operator, String reason) {
        this.ip = ip;
        this.fromState = fromState;
        this.toState = toState;
        this.interventionType = interventionType;
        this.operator = operator;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }

    public String getFromStateName() {
        return com.network.gateway.constant.IpAttackStateConstant.getStateNameZh(fromState);
    }

    public String getToStateName() {
        return com.network.gateway.constant.IpAttackStateConstant.getStateNameZh(toState);
    }

    @Override
    public String toString() {
        return String.format("StateInterventionLog{ip=%s, %s->%s, type=%s, operator=%s, reason=%s}", 
            ip, getFromStateName(), getToStateName(), interventionType, operator, reason);
    }
}
