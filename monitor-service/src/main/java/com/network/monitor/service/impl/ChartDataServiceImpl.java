package com.network.monitor.service.impl;

import com.network.monitor.bo.ChartDataBO;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.service.ChartDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ECharts 图表数据格式化服务实现类
 * 从数据库查询统计数据，按 ECharts 要求的格式做结构化封装
 */
@Slf4j
@Service
public class ChartDataServiceImpl implements ChartDataService {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public ChartDataBO getTrafficTrendLineChart(String startTime, String endTime) {
        try {
            // TODO: 实现流量趋势查询
            // 暂时返回示例数据
            List<String> xAxis = new ArrayList<>();
            List<Number> yAxis = new ArrayList<>();

            LocalDateTime now = LocalDateTime.now();
            for (int i = 23; i >= 0; i--) {
                LocalDateTime time = now.minusHours(i);
                xAxis.add(time.format(FORMATTER));
                yAxis.add((int) (Math.random() * 100));
            }

            return ChartDataBO.buildLineChart("流量趋势", xAxis, yAxis);
        } catch (Exception e) {
            log.error("获取流量趋势折线图数据失败：", e);
            return null;
        }
    }

    @Override
    public ChartDataBO getAttackTypePieChart() {
        try {
            List<AttackMonitorMapper.AttackTypeStat> stats = attackMonitorMapper.countByAttackType();

            List<Map<String, Object>> data = new ArrayList<>();
            for (AttackMonitorMapper.AttackTypeStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stat.getAttackType());
                item.put("value", stat.getCount());
                data.add(item);
            }

            return ChartDataBO.buildPieChart("攻击类型分布", data);
        } catch (Exception e) {
            log.error("获取攻击类型分布饼图数据失败：", e);
            return null;
        }
    }

    @Override
    public ChartDataBO getVulnerabilityLevelBarChart() {
        try {
            // 按漏洞等级统计
            List<String> levels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
            List<Number> counts = new ArrayList<>();

            for (String ignored : levels) {
                // TODO: 从数据库查询各等级漏洞数量
                // 暂时返回示例数据
                counts.add((int) (Math.random() * 50));
            }

            return ChartDataBO.buildBarChart("漏洞等级分布", levels, counts);
        } catch (Exception e) {
            log.error("获取漏洞等级分布柱状图数据失败：", e);
            return null;
        }
    }

    @Override
    public ChartDataBO getAttackTrendLineChart(String startTime, String endTime) {
        try {
            // TODO: 实现攻击趋势查询
            // 暂时返回示例数据
            List<String> xAxis = new ArrayList<>();
            List<Number> yAxis = new ArrayList<>();

            LocalDateTime now = LocalDateTime.now();
            for (int i = 23; i >= 0; i--) {
                LocalDateTime time = now.minusHours(i);
                xAxis.add(time.format(FORMATTER));
                yAxis.add((int) (Math.random() * 50));
            }

            return ChartDataBO.buildLineChart("攻击趋势", xAxis, yAxis);
        } catch (Exception e) {
            log.error("获取攻击趋势折线图数据失败：", e);
            return null;
        }
    }

    @Override
    public ChartDataBO getRiskLevelPieChart() {
        try {
            // 按风险等级统计
            List<String> riskLevels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
            List<Map<String, Object>> data = new ArrayList<>();

            for (String level : riskLevels) {
                // TODO: 从数据库查询各风险等级攻击数量
                // 暂时返回示例数据
                Map<String, Object> item = new HashMap<>();
                item.put("name", level);
                item.put("value", (int) (Math.random() * 100));
                data.add(item);
            }

            return ChartDataBO.buildPieChart("风险等级分布", data);
        } catch (Exception e) {
            log.error("获取风险等级分布饼图数据失败：", e);
            return null;
        }
    }

    @Override
    public ChartDataBO getTop10SourceIpBarChart() {
        try {
            // TODO: 从数据库查询 TOP10 攻击源 IP
            // 暂时返回示例数据
            List<String> ips = new ArrayList<>();
            List<Number> counts = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                ips.add("192.168.1." + (i + 1));
                counts.add((int) (Math.random() * 200));
            }

            return ChartDataBO.buildBarChart("TOP10 攻击源 IP", ips, counts);
        } catch (Exception e) {
            log.error("获取 TOP10 攻击源 IP 柱状图数据失败：", e);
            return null;
        }
    }
}
