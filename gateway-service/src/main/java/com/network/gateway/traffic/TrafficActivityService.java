package com.network.gateway.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TrafficActivityService {

    private static final Logger logger = LoggerFactory.getLogger(TrafficActivityService.class);

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong lastActivityTime = new AtomicLong(0);

    public void onTrafficReceived(String ip) {
        lastActivityTime.set(System.currentTimeMillis());
        
        if (!isActive.get()) {
            activate();
        }
    }

    public void onStateTransition(String ip, int fromState, int toState) {
        lastActivityTime.set(System.currentTimeMillis());
        
        if (!isActive.get()) {
            activate();
        }
    }

    private void activate() {
        if (isActive.compareAndSet(false, true)) {
            lastActivityTime.set(System.currentTimeMillis());
            logger.info("流量活跃服务已激活，开始处理流量数据");
        }
    }

    public void deactivate() {
        if (isActive.compareAndSet(true, false)) {
            logger.info("流量活跃服务已休眠，无流量数据");
        }
    }

    public boolean isActive() {
        return isActive.get();
    }

    public long getIdleTime() {
        return System.currentTimeMillis() - lastActivityTime.get();
    }

    public long getLastActivityTime() {
        return lastActivityTime.get();
    }

    public void updateActivityTime() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    public String getStatus() {
        return String.format("TrafficActivityService{active=%s, idleTime=%dms}", 
            isActive.get(), getIdleTime());
    }
}
