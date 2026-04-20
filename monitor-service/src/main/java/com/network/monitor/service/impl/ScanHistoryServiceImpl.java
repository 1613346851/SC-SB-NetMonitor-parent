package com.network.monitor.service.impl;

import com.network.monitor.entity.ScanHistoryEntity;
import com.network.monitor.mapper.ScanHistoryMapper;
import com.network.monitor.service.ScanHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描历史服务实现类
 */
@Slf4j
@Service
public class ScanHistoryServiceImpl implements ScanHistoryService {

    @Autowired
    private ScanHistoryMapper scanHistoryMapper;

    @Override
    public ScanHistoryEntity save(ScanHistoryEntity entity) {
        scanHistoryMapper.insert(entity);
        log.info("保存扫描历史成功：taskId={}, scanType={}", entity.getTaskId(), entity.getScanType());
        return entity;
    }

    @Override
    public ScanHistoryEntity getByTaskId(String taskId) {
        return scanHistoryMapper.selectByTaskId(taskId);
    }

    @Override
    public List<ScanHistoryEntity> getRecent(int limit) {
        return scanHistoryMapper.selectRecent(limit);
    }

    @Override
    public Map<String, Object> getPage(String scanType, String status, int page, int size) {
        int offset = (page - 1) * size;
        
        List<ScanHistoryEntity> list = scanHistoryMapper.selectByCondition(scanType, status, offset, size);
        long total = scanHistoryMapper.countByCondition(scanType, status);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }

    @Override
    public boolean update(ScanHistoryEntity entity) {
        int rows = scanHistoryMapper.update(entity);
        if (rows > 0) {
            log.info("更新扫描历史成功：taskId={}", entity.getTaskId());
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(Long id) {
        int rows = scanHistoryMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除扫描历史成功：id={}", id);
            return true;
        }
        return false;
    }
}
