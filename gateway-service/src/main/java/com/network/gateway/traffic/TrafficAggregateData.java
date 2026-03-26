package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TrafficAggregateData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int state;
    private String stateName;
    
    private long startTime;
    private long endTime;
    private long duration;
    
    private int totalRequests;
    private int errorRequests;
    private long avgProcessingTime;
    private long peakRps;
    
    private List<UriGroupStats> uriGroups;
    private List<TrafficSampleDTO> samples;
    
    private StateTransitionDTO transition;

    public TrafficAggregateData() {
    }

    public String getStateName() {
        if (stateName != null) {
            return stateName;
        }
        return getStateName(state);
    }

    private String getStateName(int state) {
        switch (state) {
            case 0: return "正常";
            case 1: return "可疑";
            case 2: return "攻击中";
            case 3: return "已防御";
            case 4: return "冷却期";
            default: return "未知";
        }
    }

    public double getErrorRate() {
        return totalRequests > 0 ? (double) errorRequests / totalRequests : 0.0;
    }

    public double getRps() {
        long durationSeconds = duration / 1000;
        return durationSeconds > 0 ? (double) totalRequests / durationSeconds : 0.0;
    }

    public boolean hasTransition() {
        return transition != null;
    }

    public int getUriGroupCount() {
        return uriGroups != null ? uriGroups.size() : 0;
    }

    public int getSampleCount() {
        return samples != null ? samples.size() : 0;
    }
}
