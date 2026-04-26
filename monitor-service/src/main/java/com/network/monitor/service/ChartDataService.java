package com.network.monitor.service;

import com.network.monitor.bo.ChartDataBO;

public interface ChartDataService {

    /**
     * 获取流量趋势折线图数据
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 流量趋势图表数据
     */
    ChartDataBO getTrafficTrendLineChart(String startTime, String endTime);

    /**
     * 获取攻击类型分布饼图数据
     *
     * @return 攻击类型分布图表数据
     */
    ChartDataBO getAttackTypePieChart();

    /**
     * 获取漏洞等级分布柱状图数据
     *
     * @return 漏洞等级分布图表数据
     */
    ChartDataBO getVulnerabilityLevelBarChart();

    /**
     * 获取攻击趋势折线图数据
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 攻击趋势图表数据
     */
    ChartDataBO getAttackTrendLineChart(String startTime, String endTime);

    /**
     * 获取风险等级分布饼图数据
     *
     * @return 风险等级分布图表数据
     */
    ChartDataBO getRiskLevelPieChart();

    /**
     * 获取 TOP10 攻击源 IP 柱状图数据
     *
     * @return TOP10 攻击源 IP 图表数据
     */
    ChartDataBO getTop10SourceIpBarChart();
}
