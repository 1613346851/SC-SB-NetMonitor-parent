package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class UriTrafficGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uriPattern;
    private String httpMethod;
    private int responseStatus;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger blockedCount = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final List<TrafficSample> samples = new ArrayList<>();
    private int maxSampleSize;
    private long startTime;
    private long lastUpdateTime;
    private boolean abnormalPriority;

    public UriTrafficGroup() {
        this.maxSampleSize = 3;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = this.startTime;
        this.abnormalPriority = true;
    }

    public UriTrafficGroup(String uriPattern, String httpMethod) {
        this();
        this.uriPattern = uriPattern;
        this.httpMethod = httpMethod;
    }

    public UriTrafficGroup(String uriPattern, String httpMethod, int maxSampleSize) {
        this(uriPattern, httpMethod);
        this.maxSampleSize = maxSampleSize;
    }

    public UriTrafficGroup(String uriPattern, String httpMethod, int responseStatus, int maxSampleSize) {
        this(uriPattern, httpMethod, maxSampleSize);
        this.responseStatus = responseStatus;
    }

    public void addRequest(TrafficSample sample) {
        count.incrementAndGet();
        lastUpdateTime = System.currentTimeMillis();
        
        if (sample.getProcessingTime() > 0) {
            totalProcessingTime.addAndGet(sample.getProcessingTime());
        }
        
        if (sample.isError()) {
            errorCount.incrementAndGet();
        }
        
        if (sample.isBlocked()) {
            blockedCount.incrementAndGet();
        }
        
        addSample(sample);
    }

    private synchronized void addSample(TrafficSample sample) {
        if (samples.size() < maxSampleSize) {
            samples.add(sample);
        } else {
            boolean replaced = false;
            
            if (abnormalPriority && sample.isAbnormalSample()) {
                for (int i = 0; i < samples.size(); i++) {
                    if (!samples.get(i).isAbnormalSample()) {
                        samples.set(i, sample);
                        replaced = true;
                        break;
                    }
                }
            }
            
            if (!replaced) {
                for (int i = 0; i < samples.size(); i++) {
                    if (samples.get(i).isSuccess() && sample.isError()) {
                        samples.set(i, sample);
                        replaced = true;
                        break;
                    }
                }
            }
            
            if (!replaced && sample.isError()) {
                samples.set(samples.size() - 1, sample);
            }
        }
    }

    public int getCount() {
        return count.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public int getBlockedCount() {
        return blockedCount.get();
    }

    public long getAverageProcessingTime() {
        int c = count.get();
        return c > 0 ? totalProcessingTime.get() / c : 0;
    }

    public double getErrorRate() {
        int c = count.get();
        return c > 0 ? (double) errorCount.get() / c : 0.0;
    }

    public double getBlockedRate() {
        int c = count.get();
        return c > 0 ? (double) blockedCount.get() / c : 0.0;
    }

    public long getDuration() {
        return lastUpdateTime - startTime;
    }

    public List<TrafficSample> getSamples() {
        return new ArrayList<>(samples);
    }

    public int getSampleCount() {
        return samples.size();
    }

    public synchronized void clearSamples() {
        samples.clear();
    }

    public synchronized void reset() {
        count.set(0);
        errorCount.set(0);
        blockedCount.set(0);
        totalProcessingTime.set(0);
        samples.clear();
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
    }

    public UriTrafficGroup copy() {
        UriTrafficGroup copy = new UriTrafficGroup(this.uriPattern, this.httpMethod, this.maxSampleSize);
        copy.count.set(this.count.get());
        copy.errorCount.set(this.errorCount.get());
        copy.blockedCount.set(this.blockedCount.get());
        copy.totalProcessingTime.set(this.totalProcessingTime.get());
        copy.startTime = this.startTime;
        copy.lastUpdateTime = this.lastUpdateTime;
        copy.abnormalPriority = this.abnormalPriority;
        synchronized (this) {
            copy.samples.addAll(this.samples);
        }
        return copy;
    }

    public UriGroupStats toStats() {
        UriGroupStats stats = new UriGroupStats();
        stats.setUriPattern(this.uriPattern);
        stats.setHttpMethod(this.httpMethod);
        stats.setResponseStatus(this.responseStatus);
        stats.setCount(this.count.get());
        stats.setErrorCount(this.errorCount.get());
        stats.setBlockedCount(this.blockedCount.get());
        stats.setAvgProcessingTime(getAverageProcessingTime());
        stats.setSampleCount(samples.size());
        return stats;
    }
}
