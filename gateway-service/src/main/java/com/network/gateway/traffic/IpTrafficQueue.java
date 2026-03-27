package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IpTrafficQueue implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int currentState;
    private final Map<Integer, StateTrafficBucket> stateBuckets = new ConcurrentHashMap<>();
    private final List<StateTransition> transitions = new ArrayList<>();
    private long lastFlushTime;
    private int maxSampleSize;
    private long createTime;

    public IpTrafficQueue() {
        this.maxSampleSize = 3;
        this.createTime = System.currentTimeMillis();
        this.lastFlushTime = this.createTime;
    }

    public IpTrafficQueue(String ip) {
        this();
        this.ip = ip;
    }

    public IpTrafficQueue(String ip, int initialState) {
        this(ip);
        this.currentState = initialState;
        getOrCreateBucket(initialState);
    }

    public IpTrafficQueue(String ip, int initialState, int maxSampleSize) {
        this(ip, initialState);
        this.maxSampleSize = maxSampleSize;
    }

    public void addRequest(TrafficSample sample) {
        StateTrafficBucket bucket = getOrCreateBucket(currentState);
        bucket.addRequest(sample);
    }

    public void transitionTo(int newState, String reason, int confidence) {
        if (newState == currentState) {
            return;
        }
        
        StateTransition transition = new StateTransition(
            currentState, newState, System.currentTimeMillis(), reason, confidence);
        transitions.add(transition);
        
        currentState = newState;
        getOrCreateBucket(newState);
    }

    private StateTrafficBucket getOrCreateBucket(int state) {
        return stateBuckets.computeIfAbsent(state, 
            s -> new StateTrafficBucket(s, maxSampleSize));
    }

    public StateTrafficBucket getCurrentBucket() {
        return getOrCreateBucket(currentState);
    }

    public StateTrafficBucket getBucket(int state) {
        return stateBuckets.get(state);
    }

    public List<StateTrafficBucket> getAllBuckets() {
        return new ArrayList<>(stateBuckets.values());
    }

    public List<StateTransition> getTransitions() {
        return new ArrayList<>(transitions);
    }

    public StateTransition getLastTransition() {
        if (transitions.isEmpty()) {
            return null;
        }
        return transitions.get(transitions.size() - 1);
    }

    public int getTotalRequestCount() {
        return stateBuckets.values().stream()
            .mapToInt(StateTrafficBucket::getTotalCount)
            .sum();
    }

    public int getTotalErrorCount() {
        return stateBuckets.values().stream()
            .mapToInt(StateTrafficBucket::getErrorCount)
            .sum();
    }

    public long getTotalProcessingTime() {
        return stateBuckets.values().stream()
            .mapToLong(bucket -> bucket.getTotalProcessingTime().get())
            .sum();
    }

    public long getAverageProcessingTime() {
        int total = getTotalRequestCount();
        return total > 0 ? getTotalProcessingTime() / total : 0;
    }

    public long getDuration() {
        return System.currentTimeMillis() - createTime;
    }

    public void markFlushed() {
        lastFlushTime = System.currentTimeMillis();
    }

    public long getTimeSinceLastFlush() {
        return System.currentTimeMillis() - lastFlushTime;
    }

    public void clearFlushedBuckets() {
        stateBuckets.entrySet().removeIf(entry -> 
            entry.getKey() != currentState && entry.getValue().isEmpty());
    }

    public void reset() {
        stateBuckets.clear();
        transitions.clear();
        lastFlushTime = System.currentTimeMillis();
        getOrCreateBucket(currentState);
    }

    public IpTrafficQueue copy() {
        IpTrafficQueue copy = new IpTrafficQueue(this.ip, this.currentState, this.maxSampleSize);
        copy.createTime = this.createTime;
        copy.lastFlushTime = this.lastFlushTime;
        
        for (Map.Entry<Integer, StateTrafficBucket> entry : stateBuckets.entrySet()) {
            copy.stateBuckets.put(entry.getKey(), entry.getValue().copy());
        }
        
        copy.transitions.addAll(this.transitions);
        
        return copy;
    }

    public TrafficAggregateData toAggregateData() {
        TrafficAggregateData data = new TrafficAggregateData();
        data.setIp(this.ip);
        data.setState(this.currentState);
        
        StateTrafficBucket currentBucket = getCurrentBucket();
        data.setStartTime(currentBucket.getStartTime());
        data.setEndTime(currentBucket.getLastUpdateTime());
        data.setDuration(currentBucket.getDuration());
        
        data.setTotalRequests(currentBucket.getTotalCount());
        data.setErrorRequests(currentBucket.getErrorCount());
        data.setAvgProcessingTime(currentBucket.getAverageProcessingTime());
        data.setPeakRps(currentBucket.getPeakRps());
        
        data.setUriGroups(currentBucket.getUriGroupStats());
        data.setSamples(convertSamples(currentBucket.getAllSamples()));
        
        StateTransition lastTransition = getLastTransition();
        if (lastTransition != null) {
            data.setTransition(lastTransition.toDTO());
        }
        
        return data;
    }

    public TrafficAggregateData toAggregateDataAndClear() {
        TrafficAggregateData data = toAggregateData();
        clearCurrentBucket();
        return data;
    }

    public void clearCurrentBucket() {
        StateTrafficBucket currentBucket = stateBuckets.get(currentState);
        if (currentBucket != null) {
            currentBucket.reset();
        }
    }

    private List<TrafficSampleDTO> convertSamples(List<TrafficSample> samples) {
        List<TrafficSampleDTO> dtos = new ArrayList<>();
        for (TrafficSample sample : samples) {
            dtos.add(new TrafficSampleDTO(sample));
        }
        return dtos;
    }

    @Data
    public static class StateTransition implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int fromState;
        private int toState;
        private long transitionTime;
        private String reason;
        private int confidence;

        public StateTransition() {
        }

        public StateTransition(int fromState, int toState, long transitionTime, String reason, int confidence) {
            this.fromState = fromState;
            this.toState = toState;
            this.transitionTime = transitionTime;
            this.reason = reason;
            this.confidence = confidence;
        }

        public StateTransitionDTO toDTO() {
            StateTransitionDTO dto = new StateTransitionDTO();
            dto.setFromState(fromState);
            dto.setToState(toState);
            dto.setTransitionTime(transitionTime);
            dto.setReason(reason);
            dto.setConfidence(confidence);
            return dto;
        }
    }
}
