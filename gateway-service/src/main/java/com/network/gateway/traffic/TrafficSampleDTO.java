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
    }
}
