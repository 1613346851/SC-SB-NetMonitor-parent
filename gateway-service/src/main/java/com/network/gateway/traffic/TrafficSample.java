package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class TrafficSample implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String eventId;
    private String requestUri;
    private String httpMethod;
    private Map<String, String> headers;
    private String requestBody;
    private int responseStatus;
    private long processingTime;
    private long timestamp;
    private boolean error;
    private String errorMessage;
    private boolean isAbnormal;
    private String traceId;
    private boolean blocked;
    private int state;
    private String stateName;
    private int confidence;
    
    private String targetIp;
    private Integer targetPort;
    private String protocol;
    private String userAgent;

    public TrafficSample() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = generateTraceId();
        this.state = 0;
        this.stateName = "NORMAL";
    }

    public TrafficSample(String requestId, String requestUri, String httpMethod) {
        this();
        this.requestId = requestId;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public boolean isError() {
        return responseStatus >= 400 || error;
    }

    public boolean isSuccess() {
        return !isError();
    }

    public boolean isAbnormalSample() {
        return responseStatus == 429 || responseStatus >= 500 || blocked;
    }

    public void setStateInfo(int state, String stateName) {
        this.state = state;
        this.stateName = stateName;
    }
    
    public String generateAggregateKey() {
        StringBuilder key = new StringBuilder();
        
        key.append(nullToEmpty(requestUri)).append("|");
        key.append(nullToEmpty(httpMethod)).append("|");
        key.append(responseStatus).append("|");
        key.append(nullToEmpty(targetIp)).append("|");
        key.append(targetPort != null ? targetPort : 0).append("|");
        key.append(nullToEmpty(protocol)).append("|");
        key.append(nullToEmpty(simplifyUserAgent(userAgent)));
        
        return key.toString();
    }
    
    private String nullToEmpty(String str) {
        return str != null ? str : "";
    }
    
    private String simplifyUserAgent(String ua) {
        if (ua == null || ua.isEmpty()) {
            return "unknown";
        }
        
        if (ua.contains("Chrome") && ua.contains("Edg")) {
            return "Edge";
        } else if (ua.contains("Chrome")) {
            return "Chrome";
        } else if (ua.contains("Firefox")) {
            return "Firefox";
        } else if (ua.contains("Safari") && !ua.contains("Chrome")) {
            return "Safari";
        } else if (ua.contains("Opera") || ua.contains("OPR")) {
            return "Opera";
        } else if (ua.contains("MSIE") || ua.contains("Trident")) {
            return "IE";
        } else if (ua.toLowerCase().contains("bot") || ua.toLowerCase().contains("crawler") || ua.toLowerCase().contains("spider")) {
            return "Bot";
        } else if (ua.contains("curl") || ua.contains("wget") || ua.contains("python-requests")) {
            return "Script";
        } else {
            if (ua.length() > 50) {
                return ua.substring(0, 50);
            }
            return ua;
        }
    }

    public static TrafficSample from(TrafficSampleDTO dto) {
        TrafficSample sample = new TrafficSample();
        sample.setRequestId(dto.getRequestId());
        sample.setRequestUri(dto.getRequestUri());
        sample.setHttpMethod(dto.getHttpMethod());
        sample.setHeaders(dto.getHeaders());
        sample.setRequestBody(dto.getRequestBody());
        sample.setResponseStatus(dto.getResponseStatus());
        sample.setProcessingTime(dto.getProcessingTime());
        sample.setError(dto.isError());
        sample.setErrorMessage(dto.getErrorMessage());
        sample.setState(dto.getState());
        sample.setStateName(dto.getStateName());
        sample.setConfidence(dto.getConfidence());
        sample.setAbnormal(sample.isAbnormalSample());
        sample.setTargetIp(dto.getTargetIp());
        sample.setTargetPort(dto.getTargetPort());
        sample.setProtocol(dto.getProtocol());
        sample.setUserAgent(dto.getUserAgent());
        return sample;
    }
}
