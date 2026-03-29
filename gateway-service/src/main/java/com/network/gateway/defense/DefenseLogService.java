package com.network.gateway.defense;

import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.dto.DefenseLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DefenseLogService {

    private static final Logger logger = LoggerFactory.getLogger(DefenseLogService.class);

    private static final long CLEANUP_INTERVAL_MS = 60000;

    private final DefenseLogAggregator aggregator;
    private final MonitorServiceDefenseClient defenseClient;
    private final ExecutorService executorService;
    
    private final AtomicLong lastCleanupTime = new AtomicLong(0);

    @Autowired
    public DefenseLogService(DefenseLogAggregator aggregator, 
                            MonitorServiceDefenseClient defenseClient) {
        this.aggregator = aggregator;
        this.defenseClient = defenseClient;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    public void recordRateLimit(String ip) {
        aggregator.recordRateLimit(ip);
        checkAndCleanup();
    }

    public void addDefenseAction(String eventId, DefenseAction action) {
        aggregator.addDefenseAction(eventId, action);
        checkAndCleanup();
    }

    public void pushDefenseLog(String eventId, String ip, Long attackId,
                               String defenseReason, Integer confidence) {
        DefenseLogDTO log = aggregator.generateAggregatedLog(
            eventId, ip, attackId, defenseReason, confidence);
        
        if (log == null) {
            logger.debug("无防御动作需要推送: eventId={}", eventId);
            return;
        }

        RateLimitCounter counter = aggregator.getAndResetRateLimitCounter(ip);
        if (counter != null && counter.getCount() > 0) {
            log.setRateLimitCount(counter.getCount());
            log.setTimeWindow(counter.getTimeWindow());
        }

        pushLogAsync(log);
    }

    public void pushRateLimitLog(String eventId, String ip, String defenseReason, 
                                 Integer confidence) {
        RateLimitCounter counter = aggregator.getAndResetRateLimitCounter(ip);
        DefenseLogDTO log = com.network.gateway.util.DefenseLogUtil.buildRateLimitAggregatedLog(
            ip, eventId, defenseReason, confidence, counter);
        
        pushLogAsync(log);
    }

    public void pushBlacklistLog(String eventId, String ip, String defenseReason,
                                 Integer confidence, Long expireTimestamp) {
        DefenseLogDTO log = com.network.gateway.util.DefenseLogUtil.buildBlacklistLog(
            ip, eventId, defenseReason, confidence, expireTimestamp);
        
        aggregator.addPendingLog(eventId, log);
        pushLogAsync(log);
    }

    public void pushBlockLog(String eventId, String ip, String defenseReason,
                            Integer confidence, String requestUri, String httpMethod) {
        DefenseLogDTO log = com.network.gateway.util.DefenseLogUtil.buildBlockLog(
            ip, eventId, defenseReason, confidence, requestUri, httpMethod);
        
        pushLogAsync(log);
    }

    public void pushCompositeLog(String eventId, String ip, Long attackId,
                                 String defenseReason, Integer confidence,
                                 List<DefenseAction> actions) {
        DefenseLogDTO log = com.network.gateway.util.DefenseLogUtil.buildCompositeLog(
            ip, eventId, attackId, defenseReason, confidence, actions);
        
        pushLogAsync(log);
    }

    private void pushLogAsync(DefenseLogDTO log) {
        executorService.submit(() -> {
            try {
                defenseClient.pushDefenseLogWithRetry(log, 3);
                logger.debug("防御日志推送成功: eventId={}, type={}", 
                    log.getEventId(), log.getDefenseType());
            } catch (Exception e) {
                logger.error("防御日志推送失败: eventId={}, type={}, error={}", 
                    log.getEventId(), log.getDefenseType(), e.getMessage());
            }
        });
    }

    public void pushLogSync(DefenseLogDTO log) {
        defenseClient.pushDefenseLogWithRetry(log, 3);
    }

    public int getRateLimitCount(String ip) {
        return aggregator.getRateLimitCount(ip);
    }

    public int getDefenseActionCount(String eventId) {
        return aggregator.getAttackDefenseCount(eventId);
    }

    private void checkAndCleanup() {
        long now = System.currentTimeMillis();
        long lastCleanup = lastCleanupTime.get();
        
        if (now - lastCleanup >= CLEANUP_INTERVAL_MS) {
            if (lastCleanupTime.compareAndSet(lastCleanup, now)) {
                cleanupExpiredData();
            }
        }
    }

    public void cleanupExpiredData() {
        aggregator.cleanupExpiredCounters();
        logger.debug("防御日志服务定时清理完成: {}", aggregator.getStats());
    }

    public String getStats() {
        return aggregator.getStats();
    }

    public void shutdown() {
        executorService.shutdown();
        logger.info("防御日志服务已关闭");
    }
}
