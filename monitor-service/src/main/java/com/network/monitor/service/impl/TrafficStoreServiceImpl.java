package com.network.monitor.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.service.TrafficStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流量数据存储服务实现类
 */
@Slf4j
@Service
public class TrafficStoreServiceImpl implements TrafficStoreService {

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Long saveTraffic(TrafficMonitorEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("流量实体对象不能为空");
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
            
            log.info("保存流量数据成功：id={}, sourceIp={}, uri={}", 
                entity.getId(), entity.getSourceIp(), entity.getRequestUri());
            
            return entity.getId();
        } catch (Exception e) {
            log.error("保存流量数据失败：entity={}, error={}", entity, e.getMessage(), e);
            throw new RuntimeException("保存流量数据失败：" + e.getMessage(), e);
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

        // 转换 Map 类型为 JSON 字符串
        if (dto.getQueryParams() != null) {
            try {
                entity.setQueryParams(mapToJson(dto.getQueryParams()));
            } catch (JsonProcessingException e) {
                log.warn("转换查询参数为 JSON 失败：{}", e.getMessage());
                entity.setQueryParams(dto.getQueryParams().toString());
            }
        }

        if (dto.getRequestHeaders() != null) {
            try {
                entity.setRequestHeaders(mapToJson(dto.getRequestHeaders()));
            } catch (JsonProcessingException e) {
                log.warn("转换请求头为 JSON 失败：{}", e.getMessage());
                entity.setRequestHeaders(dto.getRequestHeaders().toString());
            }
        }

        return entity;
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String mapToJson(Map<String, String> map) throws JsonProcessingException {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(map);
    }
}
