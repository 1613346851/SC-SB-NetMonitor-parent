package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;

/**
 * DDoS 攻击专项检测服务接口
 */
public interface DDoSDetectService {

    /**
     * 执行 DDoS 检测
     *
     * @param trafficDTO 流量数据
     * @return 攻击记录（如果检测到）
     */
    AttackMonitorDTO detect(TrafficMonitorDTO trafficDTO);

    /**
     * 重置计数器
     */
    void resetCounter(String sourceIp, String timeWindow);
}
