package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;

/**
 * 攻击数据存储服务接口
 */
public interface AttackStoreService {

    /**
     * 保存攻击记录到数据库
     */
    Long saveAttack(AttackMonitorEntity entity);

    /**
     * 从 DTO 转换为 Entity
     */
    AttackMonitorEntity convertToEntity(AttackMonitorDTO dto);
    
    /**
     * 根据事件ID更新攻击记录的流量ID
     */
    void updateTrafficIdByEventId(String eventId, Long trafficId);
}
