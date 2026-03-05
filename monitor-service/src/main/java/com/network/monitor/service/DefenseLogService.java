package com.network.monitor.service;

import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.DefenseMonitorEntity;

/**
 * 防御日志管理服务接口
 */
public interface DefenseLogService {

    /**
     * 接收并处理防御日志
     */
    void receiveDefenseLog(DefenseLogDTO logDTO);

    /**
     * 保存防御日志
     */
    void saveDefenseLog(DefenseMonitorEntity entity);
}
