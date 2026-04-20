package com.network.monitor.cache;

import com.network.monitor.common.util.IpNormalizeUtil;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BlacklistCache {

    @Autowired
    private LocalCacheService localCacheService;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    private static final String BLACKLIST_CACHE_PREFIX = "cache:blacklist:";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostConstruct
    public void init() {
        loadFromDatabase();
        log.info("黑名单缓存初始化完成");
    }

    private void loadFromDatabase() {
        try {
            List<DefenseLogEntity> allBlacklists = defenseLogMapper.selectAllBlacklists();
            for (DefenseLogEntity entity : allBlacklists) {
                if (entity.getDefenseTarget() != null) {
                    String normalizedIp = IpNormalizeUtil.normalize(entity.getDefenseTarget());
                    BlacklistEntry entry = new BlacklistEntry(
                        entity.getId(),
                        normalizedIp,
                        entity.getDefenseReason(),
                        entity.getExpireTime(),
                        entity.getCreateTime(),
                        entity.getOperator()
                    );
                    blacklistMap.put(normalizedIp, entry);

                    String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
                    localCacheService.put(cacheKey, entry, -1);
                }
            }
            log.info("从数据库加载黑名单数据完成，共{}条", allBlacklists.size());
        } catch (Exception e) {
            log.error("从数据库加载黑名单数据失败", e);
        }
    }

    private static class BlacklistEntry {
        private final Long id;
        private final String ip;
        private final String reason;
        private final LocalDateTime expireTime;
        private final LocalDateTime createTime;
        private final String operator;

        public BlacklistEntry(String ip, String reason, LocalDateTime expireTime, String operator) {
            this(null, ip, reason, expireTime, LocalDateTime.now(), operator);
        }

        public BlacklistEntry(Long id, String ip, String reason, LocalDateTime expireTime, LocalDateTime createTime, String operator) {
            this.id = id;
            this.ip = ip;
            this.reason = reason;
            this.expireTime = expireTime;
            this.createTime = createTime;
            this.operator = operator;
        }

        public Long getId() {
            return id;
        }

        public boolean isExpired() {
            return expireTime != null && LocalDateTime.now().isAfter(expireTime);
        }

        public String getIp() {
            return ip;
        }

        public String getReason() {
            return reason;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public String getOperator() {
            return operator;
        }
    }

    private final Map<String, BlacklistEntry> blacklistMap = new ConcurrentHashMap<>();

    public void add(String ip, String reason, LocalDateTime expireTime, String operator) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        String normalizedIp = IpNormalizeUtil.normalize(ip);
        BlacklistEntry entry = new BlacklistEntry(normalizedIp, reason, expireTime, operator);
        blacklistMap.put(normalizedIp, entry);

        String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
        localCacheService.put(cacheKey, entry, -1);

        log.info("添加 IP 到黑名单：ip={}, reason={}, expireTime={}, operator={}",
                normalizedIp, reason, expireTime != null ? expireTime.format(TIME_FORMATTER) : "永久", operator);
    }

    public void remove(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        String normalizedIp = IpNormalizeUtil.normalize(ip);
        blacklistMap.remove(normalizedIp);

        String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
        localCacheService.delete(cacheKey);

        log.info("从黑名单移除 IP: ip={}", normalizedIp);
    }

    public boolean contains(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String normalizedIp = IpNormalizeUtil.normalize(ip);
        BlacklistEntry entry = blacklistMap.get(normalizedIp);
        if (entry == null) {
            String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
            entry = (BlacklistEntry) localCacheService.get(cacheKey);
            if (entry != null) {
                blacklistMap.put(normalizedIp, entry);
            } else {
                return false;
            }
        }

        if (entry.isExpired()) {
            remove(normalizedIp);
            return false;
        }

        return true;
    }

    public BlacklistInfo getBlacklistInfo(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        String normalizedIp = IpNormalizeUtil.normalize(ip);
        BlacklistEntry entry = blacklistMap.get(normalizedIp);
        if (entry == null) {
            String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
            entry = (BlacklistEntry) localCacheService.get(cacheKey);
            if (entry != null) {
                blacklistMap.put(normalizedIp, entry);
            } else {
                return null;
            }
        }

        return new BlacklistInfo(
                entry.getId(),
                entry.getIp(),
                entry.getReason(),
                entry.getExpireTime() != null ? entry.getExpireTime().format(TIME_FORMATTER) : null,
                entry.getCreateTime() != null ? entry.getCreateTime().format(TIME_FORMATTER) : null,
                entry.getOperator()
        );
    }

    public BlacklistInfo getBlacklistInfoWithoutRemove(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        String normalizedIp = IpNormalizeUtil.normalize(ip);
        BlacklistEntry entry = blacklistMap.get(normalizedIp);
        if (entry == null) {
            String cacheKey = BLACKLIST_CACHE_PREFIX + normalizedIp;
            entry = (BlacklistEntry) localCacheService.get(cacheKey);
            if (entry != null) {
                blacklistMap.put(normalizedIp, entry);
            } else {
                return null;
            }
        }

        return new BlacklistInfo(
                entry.getId(),
                entry.getIp(),
                entry.getReason(),
                entry.getExpireTime() != null ? entry.getExpireTime().format(TIME_FORMATTER) : null,
                entry.getCreateTime() != null ? entry.getCreateTime().format(TIME_FORMATTER) : null,
                entry.getOperator()
        );
    }

    public int cleanExpired() {
        int count = 0;
        for (Map.Entry<String, BlacklistEntry> entry : blacklistMap.entrySet()) {
            if (entry.getValue().isExpired()) {
                blacklistMap.remove(entry.getKey());
                count++;
            }
        }

        if (count > 0) {
            log.info("清理过期黑名单 IP，数量：{}", count);
        }

        return count;
    }

    public void clear() {
        blacklistMap.clear();
        localCacheService.clearByPrefix(BLACKLIST_CACHE_PREFIX);
        log.info("清空所有黑名单缓存");
    }

    public int getSize() {
        return blacklistMap.size();
    }

    public java.util.List<String> getAllIps() {
        java.util.Set<String> allIps = new java.util.HashSet<>();
        allIps.addAll(blacklistMap.keySet());
        
        List<DefenseLogEntity> allBlacklists = defenseLogMapper.selectAllBlacklists();
        for (DefenseLogEntity entity : allBlacklists) {
            if (entity.getDefenseTarget() != null) {
                allIps.add(entity.getDefenseTarget());
            }
        }
        
        return new java.util.ArrayList<>(allIps);
    }

    public java.util.List<String> getAllIpsFromCache() {
        return new java.util.ArrayList<>(blacklistMap.keySet());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BlacklistInfo {
        private Long id;
        private String ip;
        private String reason;
        private String expireTime;
        private String createTime;
        private String operator;

        public boolean isExpired() {
            if (expireTime == null) {
                return false;
            }
            try {
                LocalDateTime expireDateTime = LocalDateTime.parse(expireTime, TIME_FORMATTER);
                return LocalDateTime.now().isAfter(expireDateTime);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
