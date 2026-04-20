package com.network.gateway.service;

import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlowAttackDetector {

    private static final Logger logger = LoggerFactory.getLogger(SlowAttackDetector.class);

    private final GatewayConfigCache configCache;
    private final Map<String, SlowAttackTracker> trackerMap;
    private final SlidingWindowRpsCalculator rpsCalculator;

    @Autowired
    public SlowAttackDetector(GatewayConfigCache configCache, SlidingWindowRpsCalculator rpsCalculator) {
        this.configCache = configCache;
        this.rpsCalculator = rpsCalculator;
        this.trackerMap = new ConcurrentHashMap<>();
    }

    public SlowAttackResult detectSlowAttack(String ip, double currentRps) {
        long durationMs = configCache.getStateSlowAttackDurationMs();
        int thresholdRps = configCache.getStateSlowAttackThresholdRps();
        
        SlowAttackTracker tracker = trackerMap.computeIfAbsent(ip, 
            k -> new SlowAttackTracker(durationMs, thresholdRps, rpsCalculator));
        
        tracker.recordRequest();
        
        return tracker.checkSlowAttack();
    }

    public void recordRequest(String ip) {
        SlowAttackTracker tracker = trackerMap.computeIfAbsent(ip, 
            k -> new SlowAttackTracker(
                configCache.getStateSlowAttackDurationMs(),
                configCache.getStateSlowAttackThresholdRps(),
                rpsCalculator
            ));
        tracker.recordRequest();
    }

    public void reset(String ip) {
        trackerMap.remove(ip);
    }

    public SlowAttackTracker getTracker(String ip) {
        return trackerMap.get(ip);
    }

    public static class SlowAttackTracker {
        private final long detectionDurationMs;
        private final int thresholdRps;
        private final SlidingWindowRpsCalculator.SlidingWindowState windowState;
        
        private long firstRequestTime;
        private long lastRequestTime;
        private int totalRequests;
        private boolean isSlowAttack;

        public SlowAttackTracker(long detectionDurationMs, int thresholdRps, 
                                  SlidingWindowRpsCalculator rpsCalculator) {
            this.detectionDurationMs = detectionDurationMs;
            this.thresholdRps = thresholdRps;
            this.windowState = rpsCalculator.createWindowState();
            this.firstRequestTime = 0;
            this.lastRequestTime = 0;
            this.totalRequests = 0;
            this.isSlowAttack = false;
        }

        public void recordRequest() {
            long now = System.currentTimeMillis();
            
            if (firstRequestTime == 0) {
                firstRequestTime = now;
            }
            
            lastRequestTime = now;
            totalRequests++;
            windowState.recordRequest();
        }

        public SlowAttackResult checkSlowAttack() {
            long now = System.currentTimeMillis();
            long duration = now - firstRequestTime;
            
            SlowAttackResult result = new SlowAttackResult();
            result.setDuration(duration);
            result.setTotalRequests(totalRequests);
            result.setThresholdRps(thresholdRps);
            
            if (duration < detectionDurationMs) {
                result.setSlowAttack(false);
                result.setReason("检测时长不足");
                return result;
            }
            
            double averageRps = totalRequests / (duration / 1000.0);
            double currentRps = windowState.calculateRps();
            
            result.setAverageRps(averageRps);
            result.setCurrentRps(currentRps);
            
            if (averageRps > 0 && averageRps <= thresholdRps && totalRequests >= 100) {
                isSlowAttack = true;
                result.setSlowAttack(true);
                result.setReason(String.format("持续低频请求: 平均RPS=%.2f, 阈值=%d, 总请求数=%d, 持续时间=%dms",
                    averageRps, thresholdRps, totalRequests, duration));
                
                return result;
            }
            
            if (isSlowAttack && currentRps <= thresholdRps) {
                result.setSlowAttack(true);
                result.setReason("慢速攻击持续中");
                return result;
            }
            
            isSlowAttack = false;
            result.setSlowAttack(false);
            result.setReason("正常请求");
            return result;
        }

        public void reset() {
            firstRequestTime = 0;
            lastRequestTime = 0;
            totalRequests = 0;
            isSlowAttack = false;
            windowState.reset();
        }

        public long getDuration() {
            if (firstRequestTime == 0) {
                return 0;
            }
            return System.currentTimeMillis() - firstRequestTime;
        }

        public int getTotalRequests() {
            return totalRequests;
        }

        public boolean isSlowAttack() {
            return isSlowAttack;
        }
    }

    public static class SlowAttackResult {
        private boolean isSlowAttack;
        private String reason;
        private long duration;
        private int totalRequests;
        private double averageRps;
        private double currentRps;
        private int thresholdRps;

        public boolean isSlowAttack() {
            return isSlowAttack;
        }

        public void setSlowAttack(boolean slowAttack) {
            isSlowAttack = slowAttack;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public int getTotalRequests() {
            return totalRequests;
        }

        public void setTotalRequests(int totalRequests) {
            this.totalRequests = totalRequests;
        }

        public double getAverageRps() {
            return averageRps;
        }

        public void setAverageRps(double averageRps) {
            this.averageRps = averageRps;
        }

        public double getCurrentRps() {
            return currentRps;
        }

        public void setCurrentRps(double currentRps) {
            this.currentRps = currentRps;
        }

        public int getThresholdRps() {
            return thresholdRps;
        }

        public void setThresholdRps(int thresholdRps) {
            this.thresholdRps = thresholdRps;
        }
    }
}
