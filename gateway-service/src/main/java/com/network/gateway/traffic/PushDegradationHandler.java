package com.network.gateway.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PushDegradationHandler {

    private static final Logger logger = LoggerFactory.getLogger(PushDegradationHandler.class);

    private final ConcurrentLinkedQueue<TrafficAggregateData> localCache = new ConcurrentLinkedQueue<>();
    
    private final AtomicBoolean degradationMode = new AtomicBoolean(false);
    
    private int maxLocalCacheSize = 5000;
    private String localCacheDir = "./logs/traffic_cache";
    private long degradationThreshold = 10;
    private long recoveryCheckInterval = 30000;
    
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger totalDegradedCount = new AtomicInteger(0);
    private final AtomicInteger recoveredCount = new AtomicInteger(0);
    private long lastRecoveryCheckTime = 0;

    public void handlePushFailure(PushTask task, Exception error) {
        int failures = consecutiveFailures.incrementAndGet();
        
        if (failures >= degradationThreshold && !degradationMode.get()) {
            enterDegradationMode();
        }
        
        if (degradationMode.get()) {
            cacheLocally(task.getData());
        }
    }

    public void handlePushSuccess() {
        consecutiveFailures.set(0);
        
        if (degradationMode.get()) {
            checkRecovery();
        }
    }

    private void enterDegradationMode() {
        if (degradationMode.compareAndSet(false, true)) {
            logger.warn("进入降级模式：推送服务不可用，开始本地缓存");
            
            try {
                Path dir = Paths.get(localCacheDir);
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            } catch (IOException e) {
                logger.error("创建本地缓存目录失败: {}", e.getMessage());
            }
        }
    }

    private void checkRecovery() {
        long now = System.currentTimeMillis();
        if (now - lastRecoveryCheckTime < recoveryCheckInterval) {
            return;
        }
        lastRecoveryCheckTime = now;
        
        if (tryFlushLocalCache()) {
            exitDegradationMode();
        }
    }

    private void exitDegradationMode() {
        if (degradationMode.compareAndSet(true, false)) {
            logger.info("退出降级模式：推送服务已恢复");
            recoveredCount.incrementAndGet();
        }
    }

    private void cacheLocally(TrafficAggregateData data) {
        if (localCache.size() >= maxLocalCacheSize) {
            flushLocalCacheToFile();
        }
        
        localCache.offer(data);
        totalDegradedCount.incrementAndGet();
        
        logger.debug("流量数据已本地缓存: ip={}, requests={}", 
            data.getIp(), data.getTotalRequests());
    }

    private void flushLocalCacheToFile() {
        if (localCache.isEmpty()) {
            return;
        }
        
        String fileName = String.format("%s/traffic_%s.log", 
            localCacheDir, 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        
        List<TrafficAggregateData> toFlush = new ArrayList<>();
        TrafficAggregateData data;
        while ((data = localCache.poll()) != null) {
            toFlush.add(data);
        }
        
        try (FileWriter writer = new FileWriter(fileName, true)) {
            for (TrafficAggregateData item : toFlush) {
                writer.write(formatDataForFile(item));
                writer.write("\n");
            }
            
            logger.info("本地缓存已写入文件: {}, 记录数: {}", fileName, toFlush.size());
            
        } catch (IOException e) {
            logger.error("写入本地缓存文件失败: {}", e.getMessage());
            for (TrafficAggregateData item : toFlush) {
                localCache.offer(item);
            }
        }
    }

    private String formatDataForFile(TrafficAggregateData data) {
        return String.format("%s|%s|%d|%d|%d|%d|%d|%s",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            data.getIp(),
            data.getState(),
            data.getTotalRequests(),
            data.getErrorRequests(),
            data.getAvgProcessingTime(),
            data.getPeakRps(),
            data.hasTransition() ? data.getTransition().getReason() : "");
    }

    private boolean tryFlushLocalCache() {
        if (localCache.isEmpty()) {
            return true;
        }
        
        logger.info("尝试恢复本地缓存数据，当前缓存数: {}", localCache.size());
        
        return false;
    }

    public boolean isInDegradationMode() {
        return degradationMode.get();
    }

    public int getLocalCacheSize() {
        return localCache.size();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void forceFlushLocalCache() {
        flushLocalCacheToFile();
    }

    public DegradationStats getStats() {
        DegradationStats stats = new DegradationStats();
        stats.setDegradationMode(degradationMode.get());
        stats.setLocalCacheSize(localCache.size());
        stats.setConsecutiveFailures(consecutiveFailures.get());
        stats.setTotalDegradedCount(totalDegradedCount.get());
        stats.setRecoveredCount(recoveredCount.get());
        return stats;
    }

    public void setMaxLocalCacheSize(int maxLocalCacheSize) {
        this.maxLocalCacheSize = maxLocalCacheSize;
    }

    public void setLocalCacheDir(String localCacheDir) {
        this.localCacheDir = localCacheDir;
    }

    public void setDegradationThreshold(long degradationThreshold) {
        this.degradationThreshold = degradationThreshold;
    }
}
