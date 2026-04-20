package com.network.gateway.service;

import com.network.gateway.cache.IpBlacklistCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.dto.BlacklistEventDTO;
import com.network.gateway.dto.DefenseLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BlacklistDegradationService {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistDegradationService.class);

    private static final String DEGRADATION_DIR = "degradation";
    private static final String BLACKLIST_FILE_PREFIX = "blacklist_";
    private static final String LOG_FILE_PREFIX = "defense_log_";
    private static final int MAX_CACHE_SIZE = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_BASE_MS = 5000;

    private final Map<String, BlacklistEventDTO> pendingBlacklistEvents = new ConcurrentHashMap<>();
    private final Map<String, DefenseLogDTO> pendingDefenseLogs = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRetryTimeMap = new ConcurrentHashMap<>();

    @Autowired
    private IpBlacklistCache blacklistCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public void onTrafficReceived(String ip) {
        if (pendingBlacklistEvents.isEmpty() && pendingDefenseLogs.isEmpty()) {
            return;
        }
        
        processPendingEventsIfNeeded();
    }

    public void processPendingEventsIfNeeded() {
        long now = System.currentTimeMillis();
        boolean hasReadyEvents = false;
        
        for (Map.Entry<String, Long> entry : lastRetryTimeMap.entrySet()) {
            AtomicInteger retryCount = retryCountMap.get(entry.getKey());
            if (retryCount != null) {
                long delay = RETRY_DELAY_BASE_MS * (1L << Math.min(retryCount.get(), 5));
                if (now - entry.getValue() >= delay) {
                    hasReadyEvents = true;
                    break;
                }
            }
        }
        
        if (hasReadyEvents || hasExpiredRetries()) {
            executorService.submit(this::retryPendingEvents);
        }
    }

    private boolean hasExpiredRetries() {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, BlacklistEventDTO> entry : pendingBlacklistEvents.entrySet()) {
            Long lastRetry = lastRetryTimeMap.get(entry.getKey());
            if (lastRetry == null || now - lastRetry > RETRY_DELAY_BASE_MS) {
                return true;
            }
        }
        
        for (Map.Entry<String, DefenseLogDTO> entry : pendingDefenseLogs.entrySet()) {
            Long lastRetry = lastRetryTimeMap.get(entry.getKey());
            if (lastRetry == null || now - lastRetry > RETRY_DELAY_BASE_MS) {
                return true;
            }
        }
        
        return false;
    }

    public void retryPendingEvents() {
        if (pendingBlacklistEvents.isEmpty() && pendingDefenseLogs.isEmpty()) {
            return;
        }

        logger.info("开始异步补推任务: 黑名单事件={}, 防御日志={}", 
            pendingBlacklistEvents.size(), pendingDefenseLogs.size());

        retryBlacklistEvents();
        retryDefenseLogs();
    }

    private void retryBlacklistEvents() {
        List<String> successKeys = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, BlacklistEventDTO> entry : pendingBlacklistEvents.entrySet()) {
            String key = entry.getKey();
            BlacklistEventDTO event = entry.getValue();
            
            Long lastRetry = lastRetryTimeMap.get(key);
            AtomicInteger retryCount = retryCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
            
            if (lastRetry != null) {
                long delay = RETRY_DELAY_BASE_MS * (1L << Math.min(retryCount.get(), 5));
                if (now - lastRetry < delay) {
                    continue;
                }
            }
            
            if (retryCount.get() >= MAX_RETRY_ATTEMPTS) {
                logger.warn("黑名单事件达到最大重试次数，保存到本地: ip={}", event.getIp());
                saveToLocalFile(event);
                successKeys.add(key);
                continue;
            }

            try {
                lastRetryTimeMap.put(key, now);
                boolean success = defenseClient.pushBlacklistEventWithRetry(event, 1);
                if (success) {
                    successKeys.add(key);
                    logger.info("异步补推黑名单事件成功: ip={}", event.getIp());
                } else {
                    retryCount.incrementAndGet();
                }
            } catch (Exception e) {
                retryCount.incrementAndGet();
                logger.warn("异步补推黑名单事件失败: ip={}, retryCount={}", 
                    event.getIp(), retryCount.get());
            }
        }

        for (String key : successKeys) {
            pendingBlacklistEvents.remove(key);
            retryCountMap.remove(key);
            lastRetryTimeMap.remove(key);
        }
    }

    private void retryDefenseLogs() {
        List<String> successKeys = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, DefenseLogDTO> entry : pendingDefenseLogs.entrySet()) {
            String key = entry.getKey();
            DefenseLogDTO log = entry.getValue();
            
            Long lastRetry = lastRetryTimeMap.get(key);
            AtomicInteger retryCount = retryCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
            
            if (lastRetry != null) {
                long delay = RETRY_DELAY_BASE_MS * (1L << Math.min(retryCount.get(), 5));
                if (now - lastRetry < delay) {
                    continue;
                }
            }
            
            if (retryCount.get() >= MAX_RETRY_ATTEMPTS) {
                logger.warn("防御日志达到最大重试次数，保存到本地: type={}, target={}", 
                    log.getDefenseType(), log.getDefenseTarget());
                saveToLocalFile(log);
                successKeys.add(key);
                continue;
            }

            try {
                lastRetryTimeMap.put(key, now);
                boolean success = defenseClient.pushDefenseLogWithRetry(log, 1);
                if (success) {
                    successKeys.add(key);
                    logger.info("异步补推防御日志成功: type={}, target={}", 
                        log.getDefenseType(), log.getDefenseTarget());
                } else {
                    retryCount.incrementAndGet();
                }
            } catch (Exception e) {
                retryCount.incrementAndGet();
                logger.warn("异步补推防御日志失败: type={}, target={}, retryCount={}", 
                    log.getDefenseType(), log.getDefenseTarget(), retryCount.get());
            }
        }

        for (String key : successKeys) {
            pendingDefenseLogs.remove(key);
            retryCountMap.remove(key);
            lastRetryTimeMap.remove(key);
        }
    }

    public void saveToDegradationCache(BlacklistEventDTO event) {
        if (event == null || event.getIp() == null) {
            return;
        }

        String key = event.getTraceId() != null ? event.getTraceId() : 
            "BL_" + event.getIp() + "_" + System.currentTimeMillis();

        if (pendingBlacklistEvents.size() >= MAX_CACHE_SIZE) {
            logger.warn("降级缓存已满，保存到本地文件: ip={}", event.getIp());
            saveToLocalFile(event);
            return;
        }

        pendingBlacklistEvents.put(key, event);
        lastRetryTimeMap.put(key, 0L);
        logger.info("黑名单事件已保存到降级缓存: ip={}, key={}", event.getIp(), key);

        if (event.getExpireTimestamp() != null && event.getExpireTimestamp() > 0) {
            blacklistCache.addToBlacklist(event.getIp(), event.getExpireTimestamp());
            logger.info("降级模式：已添加到本地黑名单缓存: ip={}", event.getIp());
        }
    }

    public void saveToDegradationCache(DefenseLogDTO log) {
        if (log == null || log.getDefenseTarget() == null) {
            return;
        }

        String key = log.getTraceId() != null ? log.getTraceId() : 
            "LOG_" + log.getDefenseTarget() + "_" + System.currentTimeMillis();

        if (pendingDefenseLogs.size() >= MAX_CACHE_SIZE) {
            logger.warn("降级缓存已满，保存到本地文件: target={}", log.getDefenseTarget());
            saveToLocalFile(log);
            return;
        }

        pendingDefenseLogs.put(key, log);
        lastRetryTimeMap.put(key, 0L);
        logger.info("防御日志已保存到降级缓存: target={}, key={}", log.getDefenseTarget(), key);
    }

    public void recordUnbanLog(DefenseLogDTO log) {
        executorService.submit(() -> {
            try {
                boolean success = defenseClient.pushDefenseLogWithRetry(log, 3);
                if (!success) {
                    saveToDegradationCache(log);
                }
            } catch (Exception e) {
                logger.error("推送解封日志失败: target={}", log.getDefenseTarget(), e);
                saveToDegradationCache(log);
            }
        });
    }

    private void saveToLocalFile(BlacklistEventDTO event) {
        try {
            Path dir = Paths.get(DEGRADATION_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = BLACKLIST_FILE_PREFIX + timestamp + "_" + event.getIp() + ".dat";
            Path file = dir.resolve(filename);

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file.toFile()))) {
                oos.writeObject(event);
            }

            logger.info("黑名单事件已保存到本地文件: {}", file);
        } catch (Exception e) {
            logger.error("保存黑名单事件到本地文件失败: ip={}", event.getIp(), e);
        }
    }

    private void saveToLocalFile(DefenseLogDTO log) {
        try {
            Path dir = Paths.get(DEGRADATION_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = LOG_FILE_PREFIX + timestamp + "_" + log.getDefenseTarget() + ".dat";
            Path file = dir.resolve(filename);

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file.toFile()))) {
                oos.writeObject(log);
            }

            logger.info("防御日志已保存到本地文件: {}", file);
        } catch (Exception e) {
            logger.error("保存防御日志到本地文件失败: target={}", log.getDefenseTarget(), e);
        }
    }

    public List<BlacklistEventDTO> loadPendingBlacklistEvents() {
        List<BlacklistEventDTO> events = new ArrayList<>();
        
        try {
            Path dir = Paths.get(DEGRADATION_DIR);
            if (!Files.exists(dir)) {
                return events;
            }

            Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith(BLACKLIST_FILE_PREFIX))
                .forEach(p -> {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(p.toFile()))) {
                        BlacklistEventDTO event = (BlacklistEventDTO) ois.readObject();
                        events.add(event);
                        Files.delete(p);
                    } catch (Exception e) {
                        logger.warn("加载黑名单事件文件失败: {}", p, e);
                    }
                });

            if (!events.isEmpty()) {
                logger.info("从本地文件加载{}个黑名单事件", events.size());
                for (BlacklistEventDTO event : events) {
                    saveToDegradationCache(event);
                }
            }
        } catch (Exception e) {
            logger.error("加载待处理黑名单事件失败", e);
        }

        return events;
    }

    public List<DefenseLogDTO> loadPendingDefenseLogs() {
        List<DefenseLogDTO> logs = new ArrayList<>();
        
        try {
            Path dir = Paths.get(DEGRADATION_DIR);
            if (!Files.exists(dir)) {
                return logs;
            }

            Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith(LOG_FILE_PREFIX))
                .forEach(p -> {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(p.toFile()))) {
                        DefenseLogDTO log = (DefenseLogDTO) ois.readObject();
                        logs.add(log);
                        Files.delete(p);
                    } catch (Exception e) {
                        logger.warn("加载防御日志文件失败: {}", p, e);
                    }
                });

            if (!logs.isEmpty()) {
                logger.info("从本地文件加载{}个防御日志", logs.size());
                for (DefenseLogDTO log : logs) {
                    saveToDegradationCache(log);
                }
            }
        } catch (Exception e) {
            logger.error("加载待处理防御日志失败", e);
        }

        return logs;
    }

    public String getStats() {
        return String.format("降级服务统计 - 待推送黑名单事件:%d, 待推送防御日志:%d, 重试记录:%d",
            pendingBlacklistEvents.size(), pendingDefenseLogs.size(), retryCountMap.size());
    }

    public int getPendingBlacklistCount() {
        return pendingBlacklistEvents.size();
    }

    public int getPendingLogCount() {
        return pendingDefenseLogs.size();
    }

    public boolean hasPendingEvents() {
        return !pendingBlacklistEvents.isEmpty() || !pendingDefenseLogs.isEmpty();
    }

    public void clearPendingEvents() {
        pendingBlacklistEvents.clear();
        pendingDefenseLogs.clear();
        retryCountMap.clear();
        lastRetryTimeMap.clear();
        logger.info("已清空所有待推送事件");
    }

    public void shutdown() {
        executorService.shutdown();
        logger.info("降级服务已关闭");
    }
}
