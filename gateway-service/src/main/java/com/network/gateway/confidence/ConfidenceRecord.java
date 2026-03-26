package com.network.gateway.confidence;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ConfidenceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int confidence;
    private int rawConfidence;
    private int delta;
    private String reason;
    private Map<String, Integer> scoreBreakdown;
    private int state;
    private String stateName;
    private long timestamp;

    public ConfidenceRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    public ConfidenceRecord(String ip, int confidence, int rawConfidence) {
        this.ip = ip;
        this.confidence = confidence;
        this.rawConfidence = rawConfidence;
        this.delta = confidence - rawConfidence;
        this.timestamp = System.currentTimeMillis();
    }

    public static ConfidenceRecord fromResult(ConfidenceResult result, int state, String reason) {
        ConfidenceRecord record = new ConfidenceRecord();
        record.setIp(result.getIp());
        record.setConfidence(result.getSmoothedConfidence());
        record.setRawConfidence(result.getRawConfidence());
        record.setDelta(result.getDelta());
        record.setReason(reason);
        record.setScoreBreakdown(result.getScoreBreakdown());
        record.setState(state);
        record.setStateName(getStateNameZh(state));
        record.setTimestamp(result.getTimestamp());
        return record;
    }

    private static String getStateNameZh(int state) {
        switch (state) {
            case 0: return "正常";
            case 1: return "可疑";
            case 2: return "攻击中";
            case 3: return "已防御";
            case 4: return "冷却期";
            default: return "未知";
        }
    }

    @Override
    public String toString() {
        return String.format("ConfidenceRecord{ip=%s, confidence=%d, raw=%d, delta=%d, state=%s, reason=%s}",
            ip, confidence, rawConfidence, delta, stateName, reason);
    }
}
