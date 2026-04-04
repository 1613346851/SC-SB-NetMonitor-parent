package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class TrafficSampleDTO implements Serializable {

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
    private int state;
    private String stateName;
    private int confidence;
    private String targetIp;
    private Integer targetPort;
    private String protocol;
    private String userAgent;
    private Integer sourcePort;

    public TrafficSampleDTO() {
    }

    public TrafficSampleDTO(TrafficSample sample) {
        this.requestId = sample.getRequestId();
        this.requestUri = sample.getRequestUri();
        this.httpMethod = sample.getHttpMethod();
        this.headers = sample.getHeaders();
        this.requestBody = sample.getRequestBody();
        this.responseStatus = sample.getResponseStatus();
        this.processingTime = sample.getProcessingTime();
        this.timestamp = sample.getTimestamp();
        this.error = sample.isError();
        this.errorMessage = sample.getErrorMessage();
        this.state = sample.getState();
        this.stateName = sample.getStateName();
        this.confidence = sample.getConfidence();
        this.targetIp = sample.getTargetIp();
        this.targetPort = sample.getTargetPort();
        this.protocol = sample.getProtocol();
        this.userAgent = sample.getUserAgent();
        this.sourcePort = sample.getSourcePort();
    }
}
