package com.network.monitor.service.impl;

import com.network.monitor.entity.ScanInterfaceEntity;
import com.network.monitor.mapper.ScanInterfaceMapper;
import com.network.monitor.service.ScanInterfaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描接口配置服务实现类
 */
@Slf4j
@Service
public class ScanInterfaceServiceImpl implements ScanInterfaceService {

    @Autowired
    private ScanInterfaceMapper scanInterfaceMapper;

    @Override
    public ScanInterfaceEntity create(ScanInterfaceEntity entity) {
        scanInterfaceMapper.insert(entity);
        log.info("创建扫描接口成功：id={}, name={}", entity.getId(), entity.getInterfaceName());
        return entity;
    }

    @Override
    public ScanInterfaceEntity getById(Long id) {
        return scanInterfaceMapper.selectById(id);
    }

    @Override
    public List<ScanInterfaceEntity> getByTargetId(Long targetId) {
        return scanInterfaceMapper.selectByTargetId(targetId);
    }

    @Override
    public List<ScanInterfaceEntity> getAllEnabled() {
        return scanInterfaceMapper.selectAllEnabled();
    }

    @Override
    public List<ScanInterfaceEntity> getAll() {
        return scanInterfaceMapper.selectAll();
    }

    @Override
    public Map<String, Object> getPage(Long targetId, String interfaceName, String vulnType, Integer enabled, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;
        
        List<ScanInterfaceEntity> list = scanInterfaceMapper.selectByCondition(targetId, interfaceName, vulnType, enabled, offset, size);
        long total = scanInterfaceMapper.countByCondition(targetId, interfaceName, vulnType, enabled);
        
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }

    @Override
    public ScanInterfaceEntity update(ScanInterfaceEntity entity) {
        scanInterfaceMapper.update(entity);
        log.info("更新扫描接口成功：id={}", entity.getId());
        return entity;
    }

    @Override
    public boolean delete(Long id) {
        int rows = scanInterfaceMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除扫描接口成功：id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteByTargetId(Long targetId) {
        int rows = scanInterfaceMapper.deleteByTargetId(targetId);
        if (rows > 0) {
            log.info("根据目标ID删除扫描接口成功：targetId={}", targetId);
            return true;
        }
        return false;
    }

    @Override
    public boolean updateEnabled(Long id, Integer enabled) {
        int rows = scanInterfaceMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            log.info("更新扫描接口启用状态成功：id={}, enabled={}", id, enabled);
            return true;
        }
        return false;
    }
}
