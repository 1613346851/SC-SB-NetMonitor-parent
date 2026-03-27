package com.network.monitor.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class TrafficAggregateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int state;
    private String stateName;
    
    private String startTime;
    private String endTime;
    private long durationMs;
    
    private int totalRequests;
    private int errorRequests;
    private long avgProcessingTime;
    private long peakRps;
    
    private List<UriGroupStatsDTO> uriGroups;
    private List<TrafficSampleDTO> samples;
    
    private StateTransitionDTO transition;
    
    private String requestId;
    private String requestTime;

    @Data
    public static class UriGroupStatsDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String uriPattern;
        private String httpMethod;
        private int count;
        private int errorCount;
        private long avgProcessingTime;
        private int sampleCount;
    }

    @Data
    public static class TrafficSampleDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private String requestUri;
        private String httpMethod;
        private Map<String, String> headers;
        private String requestBody;
        private int responseStatus;
        private long processingTime;
        private long timestamp;
        private boolean error;
        private String errorMessage;
    }

    @Data
    public static class StateTransitionDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private int fromState;
        private int toState;
        private long transitionTime;
        private String reason;
        private int confidence;
    }

    public double getErrorRate() {
        return totalRequests > 0 ? (double) errorRequests / totalRequests : 0.0;
    }

    public double getRps() {
        long durationSeconds = durationMs / 1000;
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
