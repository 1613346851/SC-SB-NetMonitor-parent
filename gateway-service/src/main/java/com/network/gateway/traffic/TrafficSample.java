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

    public TrafficSample() {
        this.timestamp = System.currentTimeMillis();
    }

    public TrafficSample(String requestId, String requestUri, String httpMethod) {
        this();
        this.requestId = requestId;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
    }

    public boolean isError() {
        return responseStatus >= 400 || error;
    }

    public boolean isSuccess() {
        return !isError();
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
        return sample;
    }
}
