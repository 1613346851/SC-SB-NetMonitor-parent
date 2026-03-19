package com.network.monitor.service.impl;

import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class DefenseLogServiceImpl implements DefenseLogService {

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void receiveDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }

        try {
            DefenseLogEntity entity = convertToEntity(logDTO);
            
            defenseLogMapper.insert(entity);
            
            log.info("接收并保存防御日志成功：attackId={}, defenseType={}, defenseAction={}, executeStatus={}", 
                logDTO.getAttackId(), logDTO.getDefenseType(), logDTO.getDefenseAction(), logDTO.getExecuteStatus());
        } catch (Exception e) {
            log.error("接收防御日志失败：", e);
        }
    }

    @Override
    public void saveDefenseLog(DefenseLogEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        if (entity.getExecuteTime() == null) {
            entity.setExecuteTime(LocalDateTime.now());
        }
        defenseLogMapper.insert(entity);
    }

    private DefenseLogEntity convertToEntity(DefenseLogDTO dto) {
        DefenseLogEntity entity = new DefenseLogEntity();
        
        entity.setDefenseType(convertDefenseType(dto.getDefenseType()));
        entity.setDefenseAction(dto.getDefenseAction());
        entity.setDefenseTarget(dto.getDefenseTarget());
        entity.setAttackId(dto.getAttackId());
        entity.setTrafficId(dto.getTrafficId());
        entity.setDefenseReason(dto.getDefenseReason());
        entity.setExecuteStatus(dto.getExecuteStatus());
        entity.setExecuteResult(dto.getExecuteResult());
        entity.setOperator(dto.getOperator() != null ? dto.getOperator() : "SYSTEM");
        
        if (dto.getExpireTime() != null && !dto.getExpireTime().isEmpty()) {
            try {
                entity.setExpireTime(LocalDateTime.parse(dto.getExpireTime().replace(" ", "T").substring(0, 19)));
            } catch (Exception e) {
                log.warn("解析过期时间失败：{}", dto.getExpireTime());
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        entity.setExecuteTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        
        return entity;
    }

    private String convertDefenseType(String originalType) {
        if (originalType == null) {
            return "BLOCK_IP";
        }
        
        switch (originalType.toUpperCase()) {
            case "IP_BLOCK":
            case "BLACKLIST":
                return "BLOCK_IP";
            case "RATE_LIMIT":
                return "RATE_LIMIT";
            case "MALICIOUS_REQUEST":
            case "BLOCK":
                return "BLOCK_REQUEST";
            default:
                return originalType.toUpperCase();
        }
    }
}
