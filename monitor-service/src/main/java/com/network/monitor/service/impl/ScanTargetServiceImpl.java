package com.network.monitor.service.impl;

import com.network.monitor.entity.ScanTargetEntity;
import com.network.monitor.mapper.ScanTargetMapper;
import com.network.monitor.service.ScanTargetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描目标配置服务实现类
 */
@Slf4j
@Service
public class ScanTargetServiceImpl implements ScanTargetService {

    @Autowired
    private ScanTargetMapper scanTargetMapper;

    @Override
    public ScanTargetEntity create(ScanTargetEntity entity) {
        scanTargetMapper.insert(entity);
        log.info("创建扫描目标成功：id={}, name={}", entity.getId(), entity.getTargetName());
        return entity;
    }

    @Override
    public ScanTargetEntity getById(Long id) {
        return scanTargetMapper.selectById(id);
    }

    @Override
    public List<ScanTargetEntity> getAllEnabled() {
        return scanTargetMapper.selectAllEnabled();
    }

    @Override
    public List<ScanTargetEntity> getAll() {
        return scanTargetMapper.selectAll();
    }

    @Override
    public Map<String, Object> getPage(String targetName, String targetType, Integer enabled, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;
        
        List<ScanTargetEntity> list = scanTargetMapper.selectByCondition(targetName, targetType, enabled, offset, size);
        long total = scanTargetMapper.countByCondition(targetName, targetType, enabled);
        
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }

    @Override
    public ScanTargetEntity update(ScanTargetEntity entity) {
        scanTargetMapper.update(entity);
        log.info("更新扫描目标成功：id={}", entity.getId());
        return entity;
    }

    @Override
    public boolean delete(Long id) {
        int rows = scanTargetMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除扫描目标成功：id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public boolean updateEnabled(Long id, Integer enabled) {
        int rows = scanTargetMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            log.info("更新扫描目标启用状态成功：id={}, enabled={}", id, enabled);
            return true;
        }
        return false;
    }
}
