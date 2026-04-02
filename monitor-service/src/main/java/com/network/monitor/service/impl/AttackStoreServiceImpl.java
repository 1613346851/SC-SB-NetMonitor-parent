package com.network.monitor.service.impl;

import com.network.monitor.dto.AlertDTO;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.service.AlertService;
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

    @Autowired
    private AlertService alertService;

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
            
            log.info("保存攻击记录成功：id={}, attackType={}, riskLevel={}, sourceIp={}, eventId={}", 
                entity.getId(), entity.getAttackType(), entity.getRiskLevel(), entity.getSourceIp(), entity.getEventId());
            
            dataPushService.pushAttackRecord(entity);
            
            triggerAlert(entity);
            
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
        entity.setHandled(0);
        
        return entity;
    }

    private void triggerAlert(AttackMonitorEntity entity) {
        try {
            Long attackId = entity.getId();
            String eventId = entity.getEventId();
            String sourceIp = entity.getSourceIp();
            String attackType = entity.getAttackType();
            String riskLevel = entity.getRiskLevel();
            
            if (attackType == null || attackType.isEmpty()) {
                attackType = "UNKNOWN";
            }
            
            if (riskLevel == null || riskLevel.isEmpty()) {
                riskLevel = "MEDIUM";
            }
            
            AlertDTO alertDTO = AlertDTO.fromAttack(attackId, eventId, sourceIp, attackType, riskLevel);
            alertService.generateAlert(alertDTO);
            
            log.info("攻击记录触发告警生成：attackId={}, eventId={}, sourceIp={}, attackType={}, riskLevel={}", 
                attackId, eventId, sourceIp, attackType, riskLevel);
        } catch (Exception e) {
            log.error("触发告警失败：attackId={}", entity.getId(), e);
        }
    }
}
