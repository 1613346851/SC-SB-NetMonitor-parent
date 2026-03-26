package com.network.gateway.traffic;

import lombok.Data;

import java.io.Serializable;

@Data
public class RetryQueueStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private int queueSize;
    private int totalRetryCount;
    private int successRetryCount;
    private int failedRetryCount;
    private int discardedCount;

    public RetryQueueStats() {
    }

    public double getSuccessRate() {
        int total = successRetryCount + failedRetryCount;
        return total > 0 ? (double) successRetryCount / total : 0.0;
    }

    @Override
    public String toString() {
        return String.format("RetryQueueStats{queueSize=%d, totalRetry=%d, success=%d, failed=%d, discarded=%d, successRate=%.2f%%}",
            queueSize, totalRetryCount, successRetryCount, failedRetryCount, discardedCount, getSuccessRate() * 100);
    }
}
