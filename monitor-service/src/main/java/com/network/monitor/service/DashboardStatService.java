package com.network.monitor.service;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计数据服务接口
 */
public interface DashboardStatService {

    /**
     * 获取仪表盘核心统计指标
     */
    Map<String, Object> getDashboardStats();

    /**
     * 获取流量趋势数据
     */
    List<Map<String, Object>> getTrafficTrend(String startTime, String endTime);

    /**
     * 获取流量趋势数据（带时间范围和统计精度）
     */
    List<Map<String, Object>> getTrafficTrend(String timeRange, String interval, boolean includeAttacks, boolean includeDefenses);

    /**
     * 获取攻击类型分布数据
     */
    List<Map<String, Object>> getAttackTypeDistribution();

    /**
     * 获取风险等级分布数据
     */
    List<Map<String, Object>> getRiskLevelDistribution();

    /**
     * 获取攻击源 IP 统计（Top 10）
     */
    List<Map<String, Object>> getTopSourceIps();

    /**
     * 获取目标 URI 统计（Top 10）
     */
    List<Map<String, Object>> getTopTargetUris();

    /**
     * 获取攻击趋势数据
     */
    List<Map<String, Object>> getAttackTrend(String startTime, String endTime);

    /**
     * 获取漏洞等级分布数据
     */
    List<Map<String, Object>> getVulnerabilityLevelDistribution();

    /**
     * 获取总流量数
     */
    long getTotalTraffic(String startTime, String endTime);

    /**
     * 获取总攻击次数
     */
    long getTotalAttacks(String startTime, String endTime);

    /**
     * 获取总漏洞数
     */
    long getTotalVulnerabilities(String startTime, String endTime);

    /**
     * 获取总防御次数
     */
    long getTotalDefenses(String startTime, String endTime);
}
