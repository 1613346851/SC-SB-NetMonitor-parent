package com.network.monitor.service;

import com.network.monitor.entity.PayloadLibraryEntity;

import java.util.List;
import java.util.Map;

/**
 * Payload库服务接口
 */
public interface PayloadLibraryService {

    /**
     * 创建Payload
     */
    PayloadLibraryEntity create(PayloadLibraryEntity entity);

    /**
     * 根据ID查询Payload
     */
    PayloadLibraryEntity getById(Long id);

    /**
     * 根据漏洞类型查询Payload
     */
    List<PayloadLibraryEntity> getByVulnType(String vulnType);

    /**
     * 根据漏洞类型和级别查询Payload
     */
    List<PayloadLibraryEntity> getByVulnTypeAndLevel(String vulnType, String payloadLevel);

    /**
     * 查询所有启用的Payload
     */
    List<PayloadLibraryEntity> getAllEnabled();

    /**
     * 查询所有Payload
     */
    List<PayloadLibraryEntity> getAll();

    /**
     * 分页查询Payload
     */
    Map<String, Object> getPage(String vulnType, String payloadLevel, String description, Integer enabled, int page, int size);

    /**
     * 更新Payload
     */
    PayloadLibraryEntity update(PayloadLibraryEntity entity);

    /**
     * 删除Payload
     */
    boolean delete(Long id);

    /**
     * 更新启用状态
     */
    boolean updateEnabled(Long id, Integer enabled);
}
