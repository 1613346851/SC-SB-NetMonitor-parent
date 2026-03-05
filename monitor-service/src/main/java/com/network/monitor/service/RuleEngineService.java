package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;

import java.util.List;

/**
 * 规则引擎服务接口
 */
public interface RuleEngineService {

    /**
     * 执行规则匹配检测
     *
     * @param trafficDTO 流量数据
     * @return 命中的攻击记录列表
     */
    List<AttackMonitorDTO> executeMatching(TrafficMonitorDTO trafficDTO);

    /**
     * SQL 注入规则匹配
     */
    List<AttackMonitorDTO> matchSqlInjection(TrafficMonitorDTO trafficDTO);

    /**
     * XSS 攻击规则匹配
     */
    List<AttackMonitorDTO> matchXss(TrafficMonitorDTO trafficDTO);

    /**
     * 命令注入规则匹配
     */
    List<AttackMonitorDTO> matchCommandInjection(TrafficMonitorDTO trafficDTO);

    /**
     * 路径遍历规则匹配
     */
    List<AttackMonitorDTO> matchPathTraversal(TrafficMonitorDTO trafficDTO);
}
