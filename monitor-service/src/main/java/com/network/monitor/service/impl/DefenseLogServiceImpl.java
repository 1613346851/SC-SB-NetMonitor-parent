package com.network.monitor.service.impl;

import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.DefenseMonitorEntity;
import com.network.monitor.mapper.DefenseMonitorMapper;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 防御日志管理服务实现类
 */
@Slf4j
@Service
public class DefenseLogServiceImpl implements DefenseLogService {

    @Autowired
    private DefenseMonitorMapper defenseMonitorMapper;

    @Override
    public void receiveDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }

        try {
            // 转换为实体类
            DefenseMonitorEntity entity = new DefenseMonitorEntity();
            BeanUtils.copyProperties(logDTO, entity);
            
            // 设置时间字段
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            
            // 保存到数据库
            saveDefenseLog(entity);
            
            log.info("接收并保存防御日志成功：attackId={}, defenseType={}, executeStatus={}", 
                logDTO.getAttackId(), logDTO.getDefenseType(), logDTO.getExecuteStatus());
        } catch (Exception e) {
            log.error("接收防御日志失败：", e);
        }
    }

    @Override
    public void saveDefenseLog(DefenseMonitorEntity entity) {
        defenseMonitorMapper.insert(entity);
    }
}
