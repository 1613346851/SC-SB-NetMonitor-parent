package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class UriGroupStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uriPattern;
    private String httpMethod;
    private int count;
    private int errorCount;
    private int blockedCount;
    private long avgProcessingTime;
    private int sampleCount;

    public UriGroupStats() {
    }

    public UriGroupStats(String uriPattern, String httpMethod, int count, int errorCount, int blockedCount, long avgProcessingTime) {
        this.uriPattern = uriPattern;
        this.httpMethod = httpMethod;
        this.count = count;
        this.errorCount = errorCount;
        this.blockedCount = blockedCount;
        this.avgProcessingTime = avgProcessingTime;
    }

    public double getErrorRate() {
        return count > 0 ? (double) errorCount / count : 0.0;
    }

    public double getBlockedRate() {
        return count > 0 ? (double) blockedCount / count : 0.0;
    }
}
