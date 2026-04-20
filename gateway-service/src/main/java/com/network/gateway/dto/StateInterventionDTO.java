package com.network.gateway.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class StateInterventionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private String interventionType;
    private String operator;
    private String reason;
    private Long duration;
    private Integer targetState;

    public static final String TYPE_FORCE_RESET = "MANUAL_RESET";
    public static final String TYPE_FORCE_BAN = "MANUAL_BAN";
    public static final String TYPE_FORCE_DEFENDED = "MANUAL_DEFENDED";
    public static final String TYPE_BATCH_RESET = "BATCH_RESET";

    public boolean isForceReset() {
        return TYPE_FORCE_RESET.equals(interventionType);
    }

    public boolean isForceBan() {
        return TYPE_FORCE_BAN.equals(interventionType);
    }

    public boolean isForceDefended() {
        return TYPE_FORCE_DEFENDED.equals(interventionType);
    }

    public boolean isBatchReset() {
        return TYPE_BATCH_RESET.equals(interventionType);
    }

    @Override
    public String toString() {
        return String.format("StateInterventionDTO{ip=%s, type=%s, operator=%s, reason=%s}", 
            ip, interventionType, operator, reason);
    }
}
