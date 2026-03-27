package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class TrafficSample implements Serializable {

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
    private boolean isAbnormal;
    private String traceId;
    private boolean blocked;
    private int state;
    private String stateName;
    private int confidence;

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
        return sample;
    }
}
