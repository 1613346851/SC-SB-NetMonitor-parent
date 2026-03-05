package com.network.monitor.service.impl;

import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.service.TrafficStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 流量数据存储服务实现类
 */
@Slf4j
@Service
public class TrafficStoreServiceImpl implements TrafficStoreService {

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @Override
    public Long saveTraffic(TrafficMonitorEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // 设置时间字段
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            if (entity.getUpdateTime() == null) {
                entity.setUpdateTime(LocalDateTime.now());
            }
            if (entity.getRequestTime() == null) {
                entity.setRequestTime(LocalDateTime.now());
            }

            trafficMonitorMapper.insert(entity);
            
            log.debug("保存流量数据成功：id={}, sourceIp={}, uri={}", 
                entity.getId(), entity.getSourceIp(), entity.getRequestUri());
            
            return entity.getId();
        } catch (Exception e) {
            log.error("保存流量数据失败：", e);
            return null;
        }
    }

    @Override
    public TrafficMonitorEntity convertToEntity(TrafficMonitorDTO dto) {
        if (dto == null) {
            return null;
        }

        TrafficMonitorEntity entity = new TrafficMonitorEntity();
        BeanUtils.copyProperties(dto, entity);
        
        // 转换时间字段
        if (dto.getRequestTime() != null && !dto.getRequestTime().isEmpty()) {
            try {
                entity.setRequestTime(LocalDateTime.parse(
                    dto.getRequestTime().replace(" ", "T")));
            } catch (Exception e) {
                entity.setRequestTime(LocalDateTime.now());
            }
        } else {
            entity.setRequestTime(LocalDateTime.now());
        }

        return entity;
    }
}
