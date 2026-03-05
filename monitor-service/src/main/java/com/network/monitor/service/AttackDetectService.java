package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;

import java.util.List;

/**
 * 攻击检测服务接口
 */
public interface AttackDetectService {

    /**
     * 执行攻击检测
     */
    List<AttackMonitorDTO> detect(TrafficMonitorDTO trafficDTO);

    /**
     * 检测 SQL 注入
     */
    List<AttackMonitorDTO> detectSqlInjection(TrafficMonitorDTO trafficDTO);

    /**
     * 检测 XSS 攻击
     */
    List<AttackMonitorDTO> detectXss(TrafficMonitorDTO trafficDTO);

    /**
     * 检测命令注入
     */
    List<AttackMonitorDTO> detectCommandInjection(TrafficMonitorDTO trafficDTO);
}
