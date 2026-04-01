package com.network.monitor.service.impl;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.service.AttackStoreService;
import com.network.monitor.websocket.DataPushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AttackStoreServiceImpl implements AttackStoreService {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private DataPushService dataPushService;

    @Override
    public Long saveAttack(AttackMonitorEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            if (entity.getHandled() == null) {
                entity.setHandled(0);
            }
            
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            if (entity.getUpdateTime() == null) {
                entity.setUpdateTime(LocalDateTime.now());
            }

            attackMonitorMapper.insert(entity);
            
            log.info("保存攻击记录成功：id={}, attackType={}, riskLevel={}, sourceIp={}", 
                entity.getId(), entity.getAttackType(), entity.getRiskLevel(), entity.getSourceIp());
            
            dataPushService.pushAttackRecord(entity);
            
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
