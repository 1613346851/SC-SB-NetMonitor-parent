package com.network.gateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 本地缓存工具类
 * 基于ConcurrentHashMap实现的线程安全缓存，支持过期时间和定时清理
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class LocalCacheUtil<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(LocalCacheUtil.class);

    /**
     * 缓存存储Map
     */
    private final ConcurrentHashMap<K, CacheEntry<V>> cacheMap;

    /**
     * 缓存名称（用于日志标识）
     */
    private final String cacheName;

    /**
     * 默认过期时间（毫秒）
     */
    private final long defaultExpireTime;

    /**
     * 定时清理执行器
     */
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 缓存条目内部类
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expireTime;
        private final long createTime;

        public CacheEntry(T value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
            this.createTime = System.currentTimeMillis();
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        public long getCreateTime() {
            return createTime;
        }

        public long getExpireTime() {
            return expireTime;
        }
    }

    /**
     * 构造函数
     *
     * @param cacheName 缓存名称
     * @param defaultExpireTime 默认过期时间（毫秒）
     * @param cleanupInterval 清理间隔（毫秒）
     */
    public LocalCacheUtil(String cacheName, long defaultExpireTime, long cleanupInterval) {
        this.cacheMap = new ConcurrentHashMap<>();
        this.cacheName = cacheName;
        this.defaultExpireTime = defaultExpireTime;
        
        // 初始化定时清理任务
        this.cleanupExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "cache-cleanup-" + cacheName);
            t.setDaemon(true);
            return t;
        });
        
        // 启动定时清理任务
        startCleanupTask(cleanupInterval);
    }

    /**
     * 启动定时清理任务
     *
     * @param interval 清理间隔（毫秒）
     */
    private void startCleanupTask(long interval) {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 
                                          interval, interval, TimeUnit.MILLISECONDS);
        logger.info("启动{}缓存清理任务，间隔{}毫秒", cacheName, interval);
    }

    /**
     * 存储键值对到缓存
     *
     * @param key 键
     * @param value 值
     */
    public void put(K key, V value) {
        put(key, value, defaultExpireTime);
    }

    /**
     * 存储键值对到缓存（指定过期时间）
     *
     * @param key 键
     * @param value 值
     * @param expireTime 过期时间（毫秒）
     */
    public void put(K key, V value, long expireTime) {
        if (key == null || value == null) {
            logger.warn("{}缓存: 尝试存储null键或值，操作被忽略", cacheName);
            return;
        }

        long actualExpireTime = System.currentTimeMillis() + expireTime;
        cacheMap.put(key, new CacheEntry<>(value, actualExpireTime));
        logger.debug("{}缓存: 存储键[{}]，过期时间[{}]", cacheName, key, actualExpireTime);
    }

    /**
     * 获取缓存值
     *
     * @param key 键
     * @return 值，如果不存在或已过期则返回null
     */
    public V get(K key) {
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            // 懒删除：发现过期时移除
            remove(key);
            logger.debug("{}缓存: 键[{}]已过期，执行懒删除", cacheName, key);
            return null;
        }

        return entry.getValue();
    }

    /**
     * 获取缓存值，如果不存在则计算并存储
     *
     * @param key 键
     * @param mappingFunction 映射函数
     * @return 值
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsent(key, mappingFunction, defaultExpireTime);
    }

    /**
     * 获取缓存值，如果不存在则计算并存储（指定过期时间）
     *
     * @param key 键
     * @param mappingFunction 映射函数
     * @param expireTime 过期时间
     * @return 值
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction, long expireTime) {
        CacheEntry<V> entry = cacheMap.get(key);
        
        // 如果存在且未过期，直接返回
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }

        // 计算新值
        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            put(key, newValue, expireTime);
        }
        
        return newValue;
    }

    /**
     * 移除缓存项
     *
     * @param key 键
     * @return 被移除的值，如果不存在则返回null
     */
    public V remove(K key) {
        CacheEntry<V> entry = cacheMap.remove(key);
        if (entry != null) {
            logger.debug("{}缓存: 移除键[{}]", cacheName, key);
            return entry.getValue();
        }
        return null;
    }

    /**
     * 检查键是否存在且未过期
     *
     * @param key 键
     * @return true表示存在且未过期
     */
    public boolean containsKey(K key) {
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry == null) {
            return false;
        }
        return !entry.isExpired();
    }

    /**
     * 获取缓存大小（不包括已过期的项）
     *
     * @return 缓存大小
     */
    public int size() {
        cleanupExpiredEntries(); // 先清理过期项
        return cacheMap.size();
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        int size = cacheMap.size();
        cacheMap.clear();
        logger.info("{}缓存: 清空所有{}个缓存项", cacheName, size);
    }

    /**
     * 清理过期的缓存项
     */
    public void cleanupExpiredEntries() {
        Iterator<Map.Entry<K, CacheEntry<V>>> iterator = cacheMap.entrySet().iterator();
        int removedCount = 0;

        while (iterator.hasNext()) {
            Map.Entry<K, CacheEntry<V>> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.debug("{}缓存: 清理了{}个过期项", cacheName, removedCount);
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        cleanupExpiredEntries(); // 先清理过期项
        return String.format("%s缓存统计 - 总数:%d 默认过期时间:%dms", 
                           cacheName, cacheMap.size(), defaultExpireTime);
    }

    /**
     * 获取所有未过期的键
     *
     * @return 键集合
     */
    public java.util.Set<K> keySet() {
        cleanupExpiredEntries();
        return cacheMap.keySet();
    }

    /**
     * 获取所有未过期的值
     *
     * @return 值集合
     */
    public java.util.Collection<V> values() {
        cleanupExpiredEntries();
        return cacheMap.values().stream()
                .filter(entry -> !entry.isExpired())
                .map(CacheEntry::getValue)
                .toList();
    }

    /**
     * 关闭缓存（停止清理任务）
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("{}缓存: 已关闭", cacheName);
    }

    /**
     * 更新现有缓存项的过期时间
     *
     * @param key 键
     * @param newExpireTime 新的过期时间（毫秒）
     * @return true表示更新成功
     */
    public boolean updateExpireTime(K key, long newExpireTime) {
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry != null) {
            long newExpireTimestamp = System.currentTimeMillis() + newExpireTime;
            cacheMap.put(key, new CacheEntry<>(entry.getValue(), newExpireTimestamp));
            logger.debug("{}缓存: 更新键[{}]的过期时间为[{}]", cacheName, key, newExpireTimestamp);
            return true;
        }
        return false;
    }

    /**
     * 获取缓存项的剩余生存时间
     *
     * @param key 键
     * @return 剩余时间（毫秒），如果不存在或已过期返回-1
     */
    public long getRemainingTime(K key) {
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry == null || entry.isExpired()) {
            return -1;
        }
        return entry.getExpireTime() - System.currentTimeMillis();
    }

    /**
     * 批量移除多个键
     *
     * @param keys 键集合
     * @return 实际移除的数量
     */
    public int removeAll(Iterable<K> keys) {
        int removedCount = 0;
        for (K key : keys) {
            if (remove(key) != null) {
                removedCount++;
            }
        }
        logger.debug("{}缓存: 批量移除{}个键", cacheName, removedCount);
        return removedCount;
    }

    /**
     * 获取缓存名称
     *
     * @return 缓存名称
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * 获取默认过期时间
     *
     * @return 默认过期时间（毫秒）
     */
    public long getDefaultExpireTime() {
        return defaultExpireTime;
    }
}