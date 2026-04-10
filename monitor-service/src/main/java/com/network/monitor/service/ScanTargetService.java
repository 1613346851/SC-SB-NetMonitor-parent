package com.network.monitor.service;

import com.network.monitor.entity.ScanTargetEntity;

import java.util.List;
import java.util.Map;

/**
 * 扫描目标配置服务接口
 */
public interface ScanTargetService {

    /**
     * 创建扫描目标
     */
    ScanTargetEntity create(ScanTargetEntity entity);

    /**
     * 根据ID查询扫描目标
     */
    ScanTargetEntity getById(Long id);

    /**
     * 查询所有启用的扫描目标
     */
    List<ScanTargetEntity> getAllEnabled();

    /**
     * 查询所有扫描目标
     */
    List<ScanTargetEntity> getAll();

    /**
     * 分页查询扫描目标
     */
    Map<String, Object> getPage(String targetName, String targetType, Integer enabled, int page, int size);

    /**
     * 更新扫描目标
     */
    ScanTargetEntity update(ScanTargetEntity entity);

    /**
     * 删除扫描目标
     */
    boolean delete(Long id);

    /**
     * 更新启用状态
     */
    boolean updateEnabled(Long id, Integer enabled);
}
