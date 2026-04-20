package com.network.gateway.cache;

import com.network.gateway.dto.TrafficMonitorDTO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RequestAggregate implements Serializable {

    private static final long serialVersionUID = 1L;

    private String aggregateKey;
    private String requestUri;
    private String httpMethod;
    private String contentType;
    private int count;
    private long totalProcessingTime;
    private int errorCount;
    private List<TrafficMonitorDTO> samples;
    private LocalDateTime firstRequestTime;
    private LocalDateTime lastRequestTime;
    private int maxSampleSize;

    public RequestAggregate() {
        this.count = 0;
        this.totalProcessingTime = 0;
        this.errorCount = 0;
        this.samples = new ArrayList<>();
        this.maxSampleSize = 5;
    }

    public RequestAggregate(String aggregateKey, int maxSampleSize) {
        this();
        this.aggregateKey = aggregateKey;
        this.maxSampleSize = maxSampleSize > 0 ? maxSampleSize : 5;
    }

    public void addRequest(TrafficMonitorDTO traffic) {
        this.count++;
        if (traffic.getProcessingTime() != null) {
            this.totalProcessingTime += traffic.getProcessingTime();
        }
        if (traffic.getSuccess() != null && !traffic.getSuccess()) {
            this.errorCount++;
        }
        
        if (this.firstRequestTime == null) {
            this.firstRequestTime = parseDateTime(traffic.getRequestTime());
        }
        this.lastRequestTime = parseDateTime(traffic.getRequestTime());
        
        if (this.requestUri == null) {
            this.requestUri = traffic.getRequestUri();
        }
        if (this.httpMethod == null) {
            this.httpMethod = traffic.getHttpMethod();
        }
        if (this.contentType == null) {
            this.contentType = traffic.getContentType();
        }
        
        if (samples.size() < maxSampleSize) {
            TrafficMonitorDTO sample = createSample(traffic);
            samples.add(sample);
        }
    }

    private TrafficMonitorDTO createSample(TrafficMonitorDTO original) {
        TrafficMonitorDTO sample = new TrafficMonitorDTO();
        sample.setRequestId(original.getRequestId());
        sample.setSourceIp(original.getSourceIp());
        sample.setTargetIp(original.getTargetIp());
        sample.setRequestUri(original.getRequestUri());
        sample.setHttpMethod(original.getHttpMethod());
        sample.setUserAgent(original.getUserAgent());
        sample.setContentType(original.getContentType());
        sample.setStateTag(original.getStateTag());
        sample.setSuccess(original.getSuccess());
        sample.setResponseStatus(original.getResponseStatus());
        sample.setProcessingTime(original.getProcessingTime());
        sample.setRequestTime(original.getRequestTime());
        sample.setRequestBody(original.getRequestBody());
        sample.setRequestHeaders(original.getRequestHeaders());
        return sample;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            if (dateTimeStr.contains("T")) {
                return LocalDateTime.parse(dateTimeStr.substring(0, 19));
            } else {
                return LocalDateTime.parse(dateTimeStr.replace(" ", "T").substring(0, 19));
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public double getAverageProcessingTime() {
        if (count == 0) {
            return 0;
        }
        return (double) totalProcessingTime / count;
    }

    public void reset() {
        this.count = 0;
        this.totalProcessingTime = 0;
        this.errorCount = 0;
        this.samples.clear();
        this.firstRequestTime = null;
        this.lastRequestTime = null;
    }

    public RequestAggregate copy() {
        RequestAggregate copy = new RequestAggregate(this.aggregateKey, this.maxSampleSize);
        copy.setRequestUri(this.requestUri);
        copy.setHttpMethod(this.httpMethod);
        copy.setContentType(this.contentType);
        copy.setCount(this.count);
        copy.setTotalProcessingTime(this.totalProcessingTime);
        copy.setErrorCount(this.errorCount);
        copy.setSamples(new ArrayList<>(this.samples));
        copy.setFirstRequestTime(this.firstRequestTime);
        copy.setLastRequestTime(this.lastRequestTime);
        return copy;
    }
}
