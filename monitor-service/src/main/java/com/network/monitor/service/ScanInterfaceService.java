package com.network.monitor.service;

import com.network.monitor.dto.ScanInterfaceRelationDTO;
import com.network.monitor.entity.ScanInterfaceEntity;

import java.util.List;
import java.util.Map;

/**
 * 扫描接口配置服务接口
 */
public interface ScanInterfaceService {

    /**
     * 根据ID查询扫描接口
     */
    ScanInterfaceEntity getById(Long id);

    /**
     * 根据ID列表批量查询扫描接口
     */
    List<ScanInterfaceEntity> getByIds(List<Long> ids);

    /**
     * 查询所有启用的扫描接口
     */
    List<ScanInterfaceEntity> getAllEnabled();

    /**
     * 查询所有扫描接口
     */
    List<ScanInterfaceEntity> getAll();

    /**
     * 查询所有扫描接口及其关联信息
     */
    List<ScanInterfaceRelationDTO> getAllWithRelations();

    /**
     * 分页查询扫描接口
     */
    Map<String, Object> getPage(Long targetId, String interfaceName, String vulnType, Integer enabled, int page, int size);

    /**
     * 更新扫描接口
     */
    ScanInterfaceEntity update(ScanInterfaceEntity entity);

    /**
     * 创建扫描接口
     */
    ScanInterfaceEntity create(ScanInterfaceEntity entity);

    /**
     * 删除扫描接口
     */
    boolean delete(Long id);

    /**
     * 根据目标ID查询扫描接口
     */
    List<ScanInterfaceEntity> getByTargetId(Long targetId);

    /**
     * 更新启用状态
     */
    boolean updateEnabled(Long id, Integer enabled);

    /**
     * 更新防御规则标记
     */
    boolean updateDefenseRule(Long id, Integer hasDefenseRule, String defenseRuleNote);
}
