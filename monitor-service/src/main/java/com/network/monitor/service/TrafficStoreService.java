package com.network.monitor.service;

import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.TrafficMonitorEntity;

/**
 * 流量数据存储服务接口
 */
public interface TrafficStoreService {

    /**
     * 保存流量数据到数据库
     */
    Long saveTraffic(TrafficMonitorEntity entity);

    /**
     * 从 DTO 转换为 Entity
     */
    TrafficMonitorEntity convertToEntity(TrafficMonitorDTO dto);
}
