package com.network.monitor.service.impl;

import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存缓存管理服务实现类
 * 基于 ConcurrentHashMap 实现线程安全的本地缓存
 */
@Slf4j
@Service
public class LocalCacheServiceImpl implements LocalCacheService {

    /**
     * 缓存数据存储
     */
    private final Map<String, CacheEntry<?>> cacheData = new ConcurrentHashMap<>();

    /**
     * 缓存条目内部类
     */
    private static class CacheEntry<T> {
        private final T value;
        private final LocalDateTime expireTime;

        public CacheEntry(T value, long expireMinutes) {
            this.value = value;
            if (expireMinutes > 0) {
                this.expireTime = LocalDateTime.now().plusMinutes(expireMinutes);
            } else {
                this.expireTime = null; // 永不过期
            }
        }

        public boolean isExpired() {
            return expireTime != null && LocalDateTime.now().isAfter(expireTime);
        }

        public T getValue() {
            return value;
        }
    }

    @Override
    public <T> T get(String key) {
        CacheEntry<?> entry = cacheData.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            delete(key);
            return null;
        }

        return (T) entry.getValue();
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, -1); // -1 表示永不过期
    }

    @Override
    public void put(String key, Object value, long expireMinutes) {
        cacheData.put(key, new CacheEntry<>(value, expireMinutes));
        log.debug("缓存写入：key={}, expireMinutes={}", key, expireMinutes);
    }

    @Override
    public void delete(String key) {
        cacheData.remove(key);
        log.debug("缓存删除：key={}", key);
    }

    @Override
    public boolean contains(String key) {
        CacheEntry<?> entry = cacheData.get(key);
        if (entry == null) {
            return false;
        }

        if (entry.isExpired()) {
            delete(key);
            return false;
        }

        return true;
    }

    @Override
    public void clear() {
        cacheData.clear();
        log.info("清空所有缓存");
    }

    @Override
    public void cleanExpired() {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, CacheEntry<?>> entry : cacheData.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }

        expiredKeys.forEach(this::delete);
        
        if (!expiredKeys.isEmpty()) {
            log.info("清理过期缓存，数量：{}", expiredKeys.size());
        }
    }

    @Override
    public int size() {
        return cacheData.size();
    }

    @Override
    public List<String> getKeysByPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        for (String key : cacheData.keySet()) {
            if (key.startsWith(prefix)) {
                result.add(key);
            }
        }
        return result;
    }

    @Override
    public void clearByPrefix(String prefix) {
        List<String> keysToDelete = getKeysByPrefix(prefix);
        keysToDelete.forEach(this::delete);
        log.info("按前缀清空缓存：prefix={}, 数量：{}", prefix, keysToDelete.size());
    }
}
