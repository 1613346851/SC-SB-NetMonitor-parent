package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class TrafficAggregatePushDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    
    private List<UriGroupStats> uriGroups;
    private List<TrafficSampleDTO> samples;
    
    private StateTransitionDTO transition;
    
    private String requestId;
    private String requestTime;

    public TrafficAggregatePushDTO() {
        this.requestId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.requestTime = LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    public TrafficAggregatePushDTO(TrafficAggregateData data) {
        this();
        this.ip = data.getIp();
        this.state = data.getState();
        this.stateName = data.getStateName();
        this.startTime = formatTime(data.getStartTime());
        this.endTime = formatTime(data.getEndTime());
        this.durationMs = data.getDuration();
        this.totalRequests = data.getTotalRequests();
        this.errorRequests = data.getErrorRequests();
        this.avgProcessingTime = data.getAvgProcessingTime();
        this.peakRps = data.getPeakRps();
        this.uriGroups = data.getUriGroups();
        this.samples = data.getSamples();
        this.transition = data.getTransition();
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        ).format(DATE_TIME_FORMATTER);
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

    public String getSummary() {
        return String.format("TrafficAggregatePushDTO{ip=%s, state=%s, requests=%d, errors=%d, rps=%.1f}",
            ip, stateName, totalRequests, errorRequests, getRps());
    }
}
