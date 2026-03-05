package com.network.monitor.cache;

import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 黑名单缓存管理类
 * 专门负责 IP 黑名单的缓存管理，与网关服务黑名单缓存保持同步
 */
@Slf4j
@Component
public class BlacklistCache {

    @Autowired
    private LocalCacheService localCacheService;

    /**
     * 黑名单缓存前缀
     */
    private static final String BLACKLIST_CACHE_PREFIX = "cache:blacklist:";

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 黑名单条目内部类
     */
    private static class BlacklistEntry {
        private final String ip;
        private final String reason;
        private final LocalDateTime expireTime;
        private final LocalDateTime createTime;
        private final String operator;

        public BlacklistEntry(String ip, String reason, LocalDateTime expireTime, String operator) {
            this.ip = ip;
            this.reason = reason;
            this.expireTime = expireTime;
            this.createTime = LocalDateTime.now();
            this.operator = operator;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
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

    /**
     * 内存中的黑名单缓存
     * Key: IP 地址
     * Value: BlacklistEntry
     */
    private final Map<String, BlacklistEntry> blacklistMap = new ConcurrentHashMap<>();

    /**
     * 添加 IP 到黑名单
     *
     * @param ip         IP 地址
     * @param reason     拉黑原因
     * @param expireTime 过期时间
     * @param operator   操作人
     */
    public void add(String ip, String reason, LocalDateTime expireTime, String operator) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        BlacklistEntry entry = new BlacklistEntry(ip, reason, expireTime, operator);
        blacklistMap.put(ip, entry);

        // 同步到全局缓存
        String cacheKey = BLACKLIST_CACHE_PREFIX + ip;
        localCacheService.put(cacheKey, entry, -1);

        log.info("添加 IP 到黑名单：ip={}, reason={}, expireTime={}, operator={}", 
                ip, reason, expireTime.format(TIME_FORMATTER), operator);
    }

    /**
     * 从黑名单移除 IP
     *
     * @param ip IP 地址
     */
    public void remove(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        blacklistMap.remove(ip);

        // 从全局缓存移除
        String cacheKey = BLACKLIST_CACHE_PREFIX + ip;
        localCacheService.delete(cacheKey);

        log.info("从黑名单移除 IP: ip={}", ip);
    }

    /**
     * 检查 IP 是否在黑名单中
     *
     * @param ip IP 地址
     * @return 是否在黑名单中
     */
    public boolean contains(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        BlacklistEntry entry = blacklistMap.get(ip);
        if (entry == null) {
            // 从全局缓存获取
            String cacheKey = BLACKLIST_CACHE_PREFIX + ip;
            entry = (BlacklistEntry) localCacheService.get(cacheKey);
            if (entry != null) {
                blacklistMap.put(ip, entry);
            } else {
                return false;
            }
        }

        // 检查是否过期
        if (entry.isExpired()) {
            remove(ip);
            return false;
        }

        return true;
    }

    /**
     * 获取黑名单条目信息
     *
     * @param ip IP 地址
     * @return 黑名单条目信息
     */
    public BlacklistInfo getBlacklistInfo(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        BlacklistEntry entry = blacklistMap.get(ip);
        if (entry == null) {
            // 从全局缓存获取
            String cacheKey = BLACKLIST_CACHE_PREFIX + ip;
            entry = (BlacklistEntry) localCacheService.get(cacheKey);
            if (entry != null) {
                blacklistMap.put(ip, entry);
            } else {
                return null;
            }
        }

        // 检查是否过期
        if (entry.isExpired()) {
            remove(ip);
            return null;
        }

        return new BlacklistInfo(
                entry.getIp(),
                entry.getReason(),
                entry.getExpireTime().format(TIME_FORMATTER),
                entry.getCreateTime().format(TIME_FORMATTER),
                entry.getOperator()
        );
    }

    /**
     * 清理过期的黑名单 IP
     *
     * @return 清理的 IP 数量
     */
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

    /**
     * 清空所有黑名单缓存
     */
    public void clear() {
        blacklistMap.clear();
        localCacheService.clearByPrefix(BLACKLIST_CACHE_PREFIX);
        log.info("清空所有黑名单缓存");
    }

    /**
     * 获取黑名单总数
     *
     * @return 黑名单总数
     */
    public int getSize() {
        return blacklistMap.size();
    }

    /**
     * 获取所有黑名单 IP 列表
     *
     * @return IP 地址列表
     */
    public java.util.List<String> getAllIps() {
        return new java.util.ArrayList<>(blacklistMap.keySet());
    }

    /**
     * 黑名单信息 DTO
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BlacklistInfo {
        private String ip;
        private String reason;
        private String expireTime;
        private String createTime;
        private String operator;
    }
}
