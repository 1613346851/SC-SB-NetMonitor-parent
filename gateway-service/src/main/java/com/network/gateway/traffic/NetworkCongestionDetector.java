package com.network.gateway.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class NetworkCongestionDetector {

    private static final Logger logger = LoggerFactory.getLogger(NetworkCongestionDetector.class);

    private static final int CONGESTION_WINDOW_MIN = 1;
    private static final int CONGESTION_WINDOW_MAX = 100;
    private static final int CONGESTION_WINDOW_DEFAULT = 20;
    
    private static final long RTT_NORMAL_MS = 100;
    private static final long RTT_WARNING_MS = 500;
    private static final long RTT_CONGESTED_MS = 1000;
    
    private static final double ERROR_RATE_NORMAL = 0.01;
    private static final double ERROR_RATE_WARNING = 0.05;
    private static final double ERROR_RATE_CONGESTED = 0.1;

    private final AtomicInteger congestionWindow = new AtomicInteger(CONGESTION_WINDOW_DEFAULT);
    private final AtomicInteger currentInflight = new AtomicInteger(0);
    private final AtomicLong lastRtt = new AtomicLong(0);
    private final AtomicLong totalPushes = new AtomicLong(0);
    private final AtomicLong failedPushes = new AtomicLong(0);
    private final AtomicLong totalRtt = new AtomicLong(0);
    private final AtomicLong rttCount = new AtomicLong(0);
    
    private final AtomicReference<CongestionState> congestionState = 
        new AtomicReference<>(CongestionState.NORMAL);

    public enum CongestionState {
        NORMAL("正常"),
        WARNING("警告"),
        CONGESTED("拥塞");
        
        private final String description;
        
        CongestionState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    public boolean canPushImmediately() {
        int inFlight = currentInflight.get();
        int window = congestionWindow.get();
        
        if (inFlight < window) {
            return true;
        }
        
        logger.debug("推送受限: inFlight={}, window={}, state={}", 
            inFlight, window, congestionState.get().getDescription());
        return false;
    }

    public void recordPushStart() {
        currentInflight.incrementAndGet();
        totalPushes.incrementAndGet();
    }

    public void recordPushSuccess(long rttMs) {
        currentInflight.decrementAndGet();
        lastRtt.set(rttMs);
        totalRtt.addAndGet(rttMs);
        rttCount.incrementAndGet();
        
        updateCongestionState(rttMs, 0);
        
        if (congestionState.get() == CongestionState.NORMAL) {
            increaseWindow();
        }
    }

    public void recordPushFailure(long rttMs, Throwable error) {
        currentInflight.decrementAndGet();
        failedPushes.incrementAndGet();
        
        if (rttMs > 0) {
            lastRtt.set(rttMs);
            totalRtt.addAndGet(rttMs);
            rttCount.incrementAndGet();
        }
        
        updateCongestionState(rttMs, getErrorRate());
        
        decreaseWindow();
        
        logger.warn("推送失败，减少拥塞窗口: window={}, error={}", 
            congestionWindow.get(), error.getMessage());
    }

    private void updateCongestionState(long rttMs, double errorRate) {
        CongestionState newState;
        
        if (rttMs > RTT_CONGESTED_MS || errorRate > ERROR_RATE_CONGESTED) {
            newState = CongestionState.CONGESTED;
        } else if (rttMs > RTT_WARNING_MS || errorRate > ERROR_RATE_WARNING) {
            newState = CongestionState.WARNING;
        } else {
            newState = CongestionState.NORMAL;
        }
        
        CongestionState oldState = congestionState.get();
        if (newState != oldState) {
            congestionState.set(newState);
            logger.info("网络状态变化: {} -> {}, rtt={}ms, errorRate={}", 
                oldState.getDescription(), newState.getDescription(), rttMs, 
                String.format("%.2f%%", errorRate * 100));
        }
    }

    private void increaseWindow() {
        int current = congestionWindow.get();
        if (current < CONGESTION_WINDOW_MAX) {
            int newWindow = Math.min(current + 1, CONGESTION_WINDOW_MAX);
            congestionWindow.compareAndSet(current, newWindow);
        }
    }

    private void decreaseWindow() {
        int current = congestionWindow.get();
        int newWindow = Math.max(current / 2, CONGESTION_WINDOW_MIN);
        congestionWindow.compareAndSet(current, newWindow);
    }

    public double getErrorRate() {
        long total = totalPushes.get();
        if (total == 0) return 0;
        return (double) failedPushes.get() / total;
    }

    public double getAverageRtt() {
        long count = rttCount.get();
        if (count == 0) return 0;
        return (double) totalRtt.get() / count;
    }

    public int getCongestionWindow() {
        return congestionWindow.get();
    }

    public int getCurrentInflight() {
        return currentInflight.get();
    }

    public CongestionState getCongestionState() {
        return congestionState.get();
    }

    public long getLastRtt() {
        return lastRtt.get();
    }

    public boolean isInCongestion() {
        return congestionState.get() == CongestionState.CONGESTED;
    }

    public boolean shouldThrottle() {
        return congestionState.get() != CongestionState.NORMAL || 
               currentInflight.get() >= congestionWindow.get();
    }

    public String getStatistics() {
        return String.format(
            "NetworkCongestionDetector{state=%s, window=%d, inFlight=%d, " +
            "avgRtt=%.1fms, errorRate=%.2f%%, totalPushes=%d}",
            congestionState.get().getDescription(),
            congestionWindow.get(),
            currentInflight.get(),
            getAverageRtt(),
            getErrorRate() * 100,
            totalPushes.get()
        );
    }

    public void reset() {
        congestionWindow.set(CONGESTION_WINDOW_DEFAULT);
        currentInflight.set(0);
        lastRtt.set(0);
        totalPushes.set(0);
        failedPushes.set(0);
        totalRtt.set(0);
        rttCount.set(0);
        congestionState.set(CongestionState.NORMAL);
        logger.info("网络拥塞检测器已重置");
    }
}
