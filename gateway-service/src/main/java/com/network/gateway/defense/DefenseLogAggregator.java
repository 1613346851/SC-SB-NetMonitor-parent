package com.network.gateway.defense;

import com.network.gateway.dto.DefenseLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefenseLogAggregator {

    private static final Logger logger = LoggerFactory.getLogger(DefenseLogAggregator.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, List<DefenseAction>> attackDefenseMap = new ConcurrentHashMap<>();
    private final Map<String, RateLimitCounter> rateLimitCounters = new ConcurrentHashMap<>();
    private final Map<String, DefenseLogDTO> pendingLogs = new ConcurrentHashMap<>();
    private final Map<String, Long> deduplicationCache = new ConcurrentHashMap<>();

    private long aggregationWindowMs = 60000;
    private int maxPendingLogs = 10000;
    private long deduplicationWindowMs = 60000;

    public void addDefenseAction(String eventId, DefenseAction action) {
        if (eventId == null || action == null) {
            return;
        }

        List<DefenseAction> actions = attackDefenseMap.computeIfAbsent(
            eventId, k -> new ArrayList<>());

        synchronized (actions) {
            boolean exists = actions.stream()
                .anyMatch(a -> a.getType().equals(action.getType()));

            if (!exists) {
                actions.add(action);
                logger.debug("添加防御动作: eventId={}, type={}", eventId, action.getType());
            } else if (action.getType().equals(DefenseLogType.RATE_LIMIT.getCode())) {
                actions.stream()
                    .filter(a -> a.getType().equals(DefenseLogType.RATE_LIMIT.getCode()))
                    .findFirst()
                    .ifPresent(DefenseAction::incrementCount);
            }
        }
    }

    public void recordRateLimit(String ip) {
        String key = buildRateLimitKey(ip);
        long now = System.currentTimeMillis();

        RateLimitCounter counter = rateLimitCounters.computeIfAbsent(
            key, k -> new RateLimitCounter(ip, now));

        if (counter.isExpired()) {
            counter = new RateLimitCounter(ip, now);
            rateLimitCounters.put(key, counter);
        }

        int count = counter.increment();
        logger.debug("记录限流: ip={}, count={}", ip, count);
    }

    public RateLimitCounter getAndResetRateLimitCounter(String ip) {
        String key = buildRateLimitKey(ip);
        RateLimitCounter counter = rateLimitCounters.remove(key);
        if (counter != null) {
            logger.debug("获取并重置限流计数器: ip={}, count={}", ip, counter.getCount());
        }
        return counter;
    }

    public boolean isDuplicate(DefenseLogDTO log) {
        if (log == null || log.getDeduplicationKey() == null) {
            return false;
        }
        
        String dedupeKey = log.getDeduplicationKey();
        Long lastTime = deduplicationCache.get(dedupeKey);
        long now = System.currentTimeMillis();
        
        if (lastTime != null && (now - lastTime) < deduplicationWindowMs) {
            logger.debug("检测到重复日志: key={}, elapsed={}ms", dedupeKey, now - lastTime);
            return true;
        }
        
        deduplicationCache.put(dedupeKey, now);
        return false;
    }

    public DefenseLogDTO generateAggregatedLog(String eventId, String ip, Long attackId, 
                                                String defenseReason, Integer confidence) {
        List<DefenseAction> actions = attackDefenseMap.remove(eventId);
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        DefenseLogDTO log = new DefenseLogDTO();
        log.setEventId(eventId);
        log.setAttackId(attackId);
        log.setDefenseTarget(ip);
        log.setDefenseReason(defenseReason);
        log.setExecuteStatus(1);
        log.setOperator("SYSTEM");
        log.setRiskLevel("HIGH");

        if (actions.size() == 1) {
            DefenseAction action = actions.get(0);
            log.setDefenseType(action.getType());
            log.setDefenseAction(action.getType());
            log.setExecuteResult(action.getDescription());
            
            if (action.getExpireTime() != null) {
                log.setExpireTime(action.getExpireTime());
            }
        } else {
            log.setDefenseType(DefenseLogType.COMPOSITE.getCode());
            log.setDefenseAction(DefenseLogType.COMPOSITE.getCode());
            log.setExecuteResult(buildCompositeDescription(actions));
        }

        log.setDefenseAction(buildDefenseActionSummary(actions));

        logger.info("生成聚合防御日志: eventId={}, ip={}, actions={}", 
            eventId, ip, actions.size());

        return log;
    }

    private String buildCompositeDescription(List<DefenseAction> actions) {
        StringBuilder sb = new StringBuilder("组合防御措施: ");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            DefenseAction action = actions.get(i);
            sb.append(action.getDescription());
            if (action.getCount() > 1) {
                sb.append("(").append(action.getCount()).append("次)");
            }
        }
        return sb.toString();
    }

    private String buildDefenseActionSummary(List<DefenseAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (DefenseAction action : actions) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(action.getType());
            if (action.getCount() > 1) {
                sb.append("(").append(action.getCount()).append(")");
            }
        }
        return sb.toString();
    }

    public void addPendingLog(String eventId, DefenseLogDTO log) {
        if (eventId == null || log == null) {
            return;
        }

        if (isDuplicate(log)) {
            logger.debug("跳过重复日志: eventId={}, type={}", eventId, log.getDefenseType());
            return;
        }

        if (pendingLogs.size() >= maxPendingLogs) {
            cleanupExpiredPendingLogs();
        }

        pendingLogs.put(eventId, log);
        logger.debug("添加待推送日志: eventId={}", eventId);
    }

    public DefenseLogDTO getPendingLog(String eventId) {
        return pendingLogs.get(eventId);
    }

    public DefenseLogDTO removePendingLog(String eventId) {
        DefenseLogDTO log = pendingLogs.remove(eventId);
        if (log != null) {
            logger.debug("移除待推送日志: eventId={}", eventId);
        }
        return log;
    }

    public boolean hasPendingLog(String eventId) {
        return pendingLogs.containsKey(eventId);
    }

    private String buildRateLimitKey(String ip) {
        long window = System.currentTimeMillis() / aggregationWindowMs;
        return ip + "_" + window;
    }

    private void cleanupExpiredPendingLogs() {
        long now = System.currentTimeMillis();
        long expireThreshold = now - 300000;

        pendingLogs.entrySet().removeIf(entry -> {
            DefenseLogDTO log = entry.getValue();
            return log.getExecuteTime() != null && log.getExecuteTime() < expireThreshold;
        });

        logger.debug("清理过期待推送日志，剩余: {}", pendingLogs.size());
    }

    public void cleanupExpiredCounters() {
        rateLimitCounters.entrySet().removeIf(entry -> 
            entry.getValue().isExpired());
        
        long now = System.currentTimeMillis();
        long dedupeThreshold = now - deduplicationWindowMs * 2;
        deduplicationCache.entrySet().removeIf(entry -> 
            entry.getValue() < dedupeThreshold);
        
        logger.debug("清理过期限流计数器，剩余: {}, 去重缓存: {}", 
            rateLimitCounters.size(), deduplicationCache.size());
    }

    public int getAttackDefenseCount(String eventId) {
        List<DefenseAction> actions = attackDefenseMap.get(eventId);
        return actions != null ? actions.size() : 0;
    }

    public int getRateLimitCount(String ip) {
        String key = buildRateLimitKey(ip);
        RateLimitCounter counter = rateLimitCounters.get(key);
        return counter != null ? counter.getCount() : 0;
    }

    public int getPendingLogCount() {
        return pendingLogs.size();
    }

    public void setAggregationWindowMs(long aggregationWindowMs) {
        this.aggregationWindowMs = aggregationWindowMs;
    }

    public void setMaxPendingLogs(int maxPendingLogs) {
        this.maxPendingLogs = maxPendingLogs;
    }

    public void setDeduplicationWindowMs(long deduplicationWindowMs) {
        this.deduplicationWindowMs = deduplicationWindowMs;
    }

    public String getStats() {
        return String.format("防御日志聚合统计 - 攻击事件数:%d, 限流计数器:%d, 待推送日志:%d, 去重缓存:%d",
            attackDefenseMap.size(), rateLimitCounters.size(), pendingLogs.size(), deduplicationCache.size());
    }
}
