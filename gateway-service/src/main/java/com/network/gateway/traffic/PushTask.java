package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class PushTask implements Serializable, Comparable<PushTask> {

    private static final long serialVersionUID = 1L;

    private long taskId;
    private PushTaskType type;
    private String ip;
    private TrafficAggregateData data;
    private long createTime;
    private int retryCount;
    private long lastRetryTime;
    private int maxRetryCount = 3;

    public PushTask() {
    }

    public boolean shouldRetry() {
        return retryCount < maxRetryCount;
    }

    public boolean isExpired(long expireMs) {
        return System.currentTimeMillis() - createTime > expireMs;
    }

    @Override
    public int compareTo(PushTask other) {
        return Long.compare(this.createTime, other.createTime);
    }

    public long getAge() {
        return System.currentTimeMillis() - createTime;
    }
}
