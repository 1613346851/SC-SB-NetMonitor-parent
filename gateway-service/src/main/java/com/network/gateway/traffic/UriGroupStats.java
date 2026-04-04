package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class UriGroupStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uriPattern;
    private String httpMethod;
    private int responseStatus;
    private String targetIp;
    private Integer targetPort;
    private String protocol;
    private String userAgent;
    private Integer sourcePort;
    private Map<String, String> headers;
    private int count;
    private int errorCount;
    private int blockedCount;
    private long avgProcessingTime;
    private int sampleCount;

    public UriGroupStats() {
    }

    public UriGroupStats(String uriPattern, String httpMethod, int responseStatus, int count, int errorCount, int blockedCount, long avgProcessingTime) {
        this.uriPattern = uriPattern;
        this.httpMethod = httpMethod;
        this.responseStatus = responseStatus;
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
