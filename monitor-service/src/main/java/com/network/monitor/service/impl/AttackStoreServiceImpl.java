package com.network.monitor.service.impl;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.service.AttackStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 攻击数据存储服务实现类
 */
@Slf4j
@Service
public class AttackStoreServiceImpl implements AttackStoreService {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Override
    public Long saveAttack(AttackMonitorEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // 设置默认值
            if (entity.getHandled() == null) {
                entity.setHandled(0); // 默认未处理
            }
            
            // 设置时间字段
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            if (entity.getUpdateTime() == null) {
                entity.setUpdateTime(LocalDateTime.now());
            }

            attackMonitorMapper.insert(entity);
            
            log.info("保存攻击记录成功：id={}, attackType={}, riskLevel={}, sourceIp={}", 
                entity.getId(), entity.getAttackType(), entity.getRiskLevel(), entity.getSourceIp());
            
            return entity.getId();
        } catch (Exception e) {
            log.error("保存攻击记录失败：", e);
            return null;
        }
    }

    @Override
    public AttackMonitorEntity convertToEntity(AttackMonitorDTO dto) {
        if (dto == null) {
            return null;
        }

        AttackMonitorEntity entity = new AttackMonitorEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setHandled(0); // 默认未处理
        
        return entity;
    }
}
