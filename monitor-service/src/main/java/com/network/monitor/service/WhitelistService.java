package com.network.monitor.service;

import com.network.monitor.entity.WhitelistEntity;

import java.util.List;

/**
 * 白名单管理服务接口
 */
public interface WhitelistService {

    /**
     * 获取所有启用的白名单
     */
    List<WhitelistEntity> getAllEnabled();

    /**
     * 获取所有白名单
     */
    List<WhitelistEntity> getAll();

    /**
     * 根据类型获取白名单
     */
    List<WhitelistEntity> getByType(String whitelistType);

    /**
     * 根据ID获取白名单
     */
    WhitelistEntity getById(Long id);

    /**
     * 分页查询白名单
     */
    List<WhitelistEntity> getByCondition(String whitelistType, String whitelistValue, Integer enabled, int page, int size);

    /**
     * 统计白名单数量
     */
    long countByCondition(String whitelistType, String whitelistValue, Integer enabled);

    /**
     * 添加白名单
     */
    WhitelistEntity add(WhitelistEntity entity);

    /**
     * 更新白名单
     */
    WhitelistEntity update(WhitelistEntity entity);

    /**
     * 删除白名单
     */
    boolean delete(Long id);

    /**
     * 启用/禁用白名单
     */
    boolean toggleEnabled(Long id, Integer enabled);

    /**
     * 刷新白名单缓存
     */
    void refreshWhitelistCache();
}
