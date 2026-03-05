package com.network.monitor.service;

import java.util.List;
import java.util.Map;

/**
 * 本地内存缓存管理服务接口
 */
public interface LocalCacheService {

    /**
     * 从缓存获取数据
     */
    <T> T get(String key);

    /**
     * 向缓存写入数据（永不过期）
     */
    void put(String key, Object value);

    /**
     * 向缓存写入数据（带过期时间）
     */
    void put(String key, Object value, long expireMinutes);

    /**
     * 从缓存删除数据
     */
    void delete(String key);

    /**
     * 检查缓存是否存在
     */
    boolean contains(String key);

    /**
     * 清空所有缓存
     */
    void clear();

    /**
     * 清理过期缓存
     */
    void cleanExpired();

    /**
     * 获取缓存大小
     */
    int size();

    /**
     * 按前缀获取所有键
     */
    List<String> getKeysByPrefix(String prefix);

    /**
     * 按前缀清空缓存
     */
    void clearByPrefix(String prefix);
}
