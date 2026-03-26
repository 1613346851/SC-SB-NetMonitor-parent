package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class StateTrafficBucket implements Serializable {

    private static final long serialVersionUID = 1L;

    private int state;
    private long startTime;
    private long lastUpdateTime;
    private final Map<String, UriTrafficGroup> uriGroups = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong peakRps = new AtomicLong(0);
    private int maxSampleSize;
    private long rpsWindowStart;
    private final AtomicInteger rpsWindowCount = new AtomicInteger(0);

    public StateTrafficBucket() {
        this.maxSampleSize = 3;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = this.startTime;
        this.rpsWindowStart = this.startTime;
    }

    public StateTrafficBucket(int state) {
        this();
        this.state = state;
    }

    public StateTrafficBucket(int state, int maxSampleSize) {
        this(state);
        this.maxSampleSize = maxSampleSize;
    }

    public void addRequest(TrafficSample sample) {
        totalCount.incrementAndGet();
        lastUpdateTime = System.currentTimeMillis();
        
        if (sample.getProcessingTime() > 0) {
            totalProcessingTime.addAndGet(sample.getProcessingTime());
        }
        
        if (sample.isError()) {
            errorCount.incrementAndGet();
        }
        
        updatePeakRps();
        
        String uriKey = buildUriKey(sample.getRequestUri(), sample.getHttpMethod());
        UriTrafficGroup group = uriGroups.computeIfAbsent(uriKey, 
            k -> new UriTrafficGroup(sample.getRequestUri(), sample.getHttpMethod(), maxSampleSize));
        group.addRequest(sample);
    }

    private void updatePeakRps() {
        long now = System.currentTimeMillis();
        long elapsed = now - rpsWindowStart;
        
        if (elapsed >= 1000) {
            synchronized (this) {
                if (now - rpsWindowStart >= 1000) {
                    int currentRps = rpsWindowCount.getAndSet(1);
                    if (currentRps > peakRps.get()) {
                        peakRps.set(currentRps);
                    }
                    rpsWindowStart = now;
                } else {
                    rpsWindowCount.incrementAndGet();
                }
            }
        } else {
            rpsWindowCount.incrementAndGet();
        }
    }

    private String buildUriKey(String uri, String method) {
        String pattern = extractUriPattern(uri);
        return method + ":" + pattern;
    }

    private String extractUriPattern(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        
        if (uri.length() > 50) {
            return uri.substring(0, 50) + "...";
        }
        
        return uri;
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public long getAverageProcessingTime() {
        int c = totalCount.get();
        return c > 0 ? totalProcessingTime.get() / c : 0;
    }

    public double getErrorRate() {
        int c = totalCount.get();
        return c > 0 ? (double) errorCount.get() / c : 0.0;
    }

    public long getDuration() {
        return lastUpdateTime - startTime;
    }

    public long getPeakRps() {
        return peakRps.get();
    }

    public List<UriTrafficGroup> getUriGroups() {
        return new ArrayList<>(uriGroups.values());
    }

    public List<UriGroupStats> getUriGroupStats() {
        List<UriGroupStats> stats = new ArrayList<>();
        for (UriTrafficGroup group : uriGroups.values()) {
            stats.add(group.toStats());
        }
        return stats;
    }

    public int getUriGroupCount() {
        return uriGroups.size();
    }

    public List<TrafficSample> getAllSamples() {
        List<TrafficSample> allSamples = new ArrayList<>();
        for (UriTrafficGroup group : uriGroups.values()) {
            allSamples.addAll(group.getSamples());
        }
        return allSamples;
    }

    public void reset() {
        totalCount.set(0);
        errorCount.set(0);
        totalProcessingTime.set(0);
        peakRps.set(0);
        rpsWindowCount.set(0);
        uriGroups.clear();
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
        rpsWindowStart = startTime;
    }

    public StateTrafficBucket copy() {
        StateTrafficBucket copy = new StateTrafficBucket(this.state, this.maxSampleSize);
        copy.totalCount.set(this.totalCount.get());
        copy.errorCount.set(this.errorCount.get());
        copy.totalProcessingTime.set(this.totalProcessingTime.get());
        copy.peakRps.set(this.peakRps.get());
        copy.startTime = this.startTime;
        copy.lastUpdateTime = this.lastUpdateTime;
        
        for (Map.Entry<String, UriTrafficGroup> entry : uriGroups.entrySet()) {
            copy.uriGroups.put(entry.getKey(), entry.getValue().copy());
        }
        
        return copy;
    }

    public boolean isEmpty() {
        return totalCount.get() == 0;
    }

    public boolean isExpired(long windowMs) {
        return System.currentTimeMillis() - lastUpdateTime > windowMs;
    }
}
