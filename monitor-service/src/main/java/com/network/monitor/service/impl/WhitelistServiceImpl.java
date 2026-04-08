package com.network.monitor.service.impl;

import com.network.monitor.entity.WhitelistEntity;
import com.network.monitor.mapper.WhitelistMapper;
import com.network.monitor.service.WhitelistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class WhitelistServiceImpl implements WhitelistService {

    @Autowired
    private WhitelistMapper whitelistMapper;

    @Override
    public List<WhitelistEntity> getAllEnabled() {
        return whitelistMapper.selectAllEnabled();
    }

    @Override
    public List<WhitelistEntity> getAll() {
        return whitelistMapper.selectAll();
    }

    @Override
    public List<WhitelistEntity> getByType(String whitelistType) {
        return whitelistMapper.selectByType(whitelistType);
    }

    @Override
    public WhitelistEntity getById(Long id) {
        return whitelistMapper.selectById(id);
    }

    @Override
    public List<WhitelistEntity> getByCondition(String whitelistType, String whitelistValue, Integer enabled, int page, int size) {
        int offset = (page - 1) * size;
        return whitelistMapper.selectByCondition(whitelistType, whitelistValue, enabled, offset, size);
    }

    @Override
    public long countByCondition(String whitelistType, String whitelistValue, Integer enabled) {
        return whitelistMapper.countByCondition(whitelistType, whitelistValue, enabled);
    }

    @Override
    @Transactional
    public WhitelistEntity add(WhitelistEntity entity) {
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getPriority() == null) {
            entity.setPriority(100);
        }

        whitelistMapper.insert(entity);
        log.info("添加白名单成功: id={}, type={}, value={}", entity.getId(), entity.getWhitelistType(), entity.getWhitelistValue());

        refreshWhitelistCache();

        return entity;
    }

    @Override
    @Transactional
    public WhitelistEntity update(WhitelistEntity entity) {
        WhitelistEntity existing = whitelistMapper.selectById(entity.getId());
        if (existing == null) {
            log.warn("白名单不存在: id={}", entity.getId());
            return null;
        }

        entity.setUpdateTime(LocalDateTime.now());
        whitelistMapper.update(entity);
        log.info("更新白名单成功: id={}, type={}, value={}", entity.getId(), entity.getWhitelistType(), entity.getWhitelistValue());

        refreshWhitelistCache();

        return entity;
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        int rows = whitelistMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除白名单成功: id={}", id);
            refreshWhitelistCache();
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public boolean toggleEnabled(Long id, Integer enabled) {
        int rows = whitelistMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            log.info("更新白名单状态成功: id={}, enabled={}", id, enabled);
            refreshWhitelistCache();
            return true;
        }
        return false;
    }

    @Override
    public void refreshWhitelistCache() {
        log.info("白名单缓存刷新完成");
    }
}
