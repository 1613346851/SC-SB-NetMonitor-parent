package com.network.monitor.service;

import com.network.monitor.entity.ScanHistoryEntity;

import java.util.List;
import java.util.Map;

/**
 * 扫描历史服务接口
 */
public interface ScanHistoryService {

    /**
     * 保存扫描历史
     */
    ScanHistoryEntity save(ScanHistoryEntity entity);

    /**
     * 根据任务ID查询
     */
    ScanHistoryEntity getByTaskId(String taskId);

    /**
     * 查询最近的扫描历史
     */
    List<ScanHistoryEntity> getRecent(int limit);

    /**
     * 分页查询扫描历史
     */
    Map<String, Object> getPage(String scanType, String status, int page, int size);

    /**
     * 更新扫描历史
     */
    boolean update(ScanHistoryEntity entity);

    /**
     * 删除扫描历史
     */
    boolean delete(Long id);
}
