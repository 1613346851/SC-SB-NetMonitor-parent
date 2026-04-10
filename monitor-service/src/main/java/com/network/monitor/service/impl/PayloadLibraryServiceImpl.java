package com.network.monitor.service.impl;

import com.network.monitor.entity.PayloadLibraryEntity;
import com.network.monitor.mapper.PayloadLibraryMapper;
import com.network.monitor.service.PayloadLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payload库服务实现类
 */
@Slf4j
@Service
public class PayloadLibraryServiceImpl implements PayloadLibraryService {

    @Autowired
    private PayloadLibraryMapper payloadLibraryMapper;

    @Override
    public PayloadLibraryEntity create(PayloadLibraryEntity entity) {
        payloadLibraryMapper.insert(entity);
        log.info("创建Payload成功：id={}, vulnType={}", entity.getId(), entity.getVulnType());
        return entity;
    }

    @Override
    public PayloadLibraryEntity getById(Long id) {
        return payloadLibraryMapper.selectById(id);
    }

    @Override
    public List<PayloadLibraryEntity> getByVulnType(String vulnType) {
        return payloadLibraryMapper.selectByVulnType(vulnType);
    }

    @Override
    public List<PayloadLibraryEntity> getByVulnTypeAndLevel(String vulnType, String payloadLevel) {
        return payloadLibraryMapper.selectByVulnTypeAndLevel(vulnType, payloadLevel);
    }

    @Override
    public List<PayloadLibraryEntity> getAllEnabled() {
        return payloadLibraryMapper.selectAllEnabled();
    }

    @Override
    public List<PayloadLibraryEntity> getAll() {
        return payloadLibraryMapper.selectAll();
    }

    @Override
    public Map<String, Object> getPage(String vulnType, String payloadLevel, String description, Integer enabled, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;
        
        List<PayloadLibraryEntity> list = payloadLibraryMapper.selectByCondition(vulnType, payloadLevel, description, enabled, offset, size);
        long total = payloadLibraryMapper.countByCondition(vulnType, payloadLevel, description, enabled);
        
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }

    @Override
    public PayloadLibraryEntity update(PayloadLibraryEntity entity) {
        payloadLibraryMapper.update(entity);
        log.info("更新Payload成功：id={}", entity.getId());
        return entity;
    }

    @Override
    public boolean delete(Long id) {
        int rows = payloadLibraryMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除Payload成功：id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public boolean updateEnabled(Long id, Integer enabled) {
        int rows = payloadLibraryMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            log.info("更新Payload启用状态成功：id={}, enabled={}", id, enabled);
            return true;
        }
        return false;
    }
}
