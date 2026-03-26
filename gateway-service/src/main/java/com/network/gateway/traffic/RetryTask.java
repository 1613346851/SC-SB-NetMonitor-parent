package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class RetryTask implements Comparable<RetryTask>, Serializable {

    private static final long serialVersionUID = 1L;

    private PushTask task;
    private Exception error;
    private long createTime;
    private long nextRetryTime;
    private static final long[] RETRY_DELAYS = {0, 1000, 3000};

    public RetryTask(PushTask task, Exception error) {
        this.task = task;
        this.error = error;
        this.createTime = System.currentTimeMillis();
        calculateNextRetryTime();
    }

    private void calculateNextRetryTime() {
        int retryCount = task.getRetryCount();
        long delay = 0;
        if (retryCount < RETRY_DELAYS.length) {
            delay = RETRY_DELAYS[retryCount];
        } else {
            delay = RETRY_DELAYS[RETRY_DELAYS.length - 1];
        }
        this.nextRetryTime = createTime + delay;
    }

    public boolean isReadyToRetry() {
        return System.currentTimeMillis() >= nextRetryTime;
    }

    public boolean isExpired(long expireMs) {
        return System.currentTimeMillis() - createTime > expireMs;
    }

    public long getTimeUntilRetry() {
        return Math.max(0, nextRetryTime - System.currentTimeMillis());
    }

    @Override
    public int compareTo(RetryTask other) {
        return Long.compare(this.nextRetryTime, other.nextRetryTime);
    }
}
