package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.service.DashboardStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据报表接口控制器
 * 为前端报表页面提供统计数据接口
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private DashboardStatService dashboardStatService;

    /**
     * 获取报表核心统计汇总
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getReportSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            // 总流量数
            long totalTraffic = dashboardStatService.getTotalTraffic(startDate, endDate);
            summary.put("totalTraffic", totalTraffic);
            
            // 总攻击次数
            long totalAttacks = dashboardStatService.getTotalAttacks(startDate, endDate);
            summary.put("totalAttacks", totalAttacks);
            
            // 总漏洞数
            long totalVulnerabilities = dashboardStatService.getTotalVulnerabilities(startDate, endDate);
            summary.put("totalVulnerabilities", totalVulnerabilities);
            
            // 总防御次数
            long totalDefenses = dashboardStatService.getTotalDefenses(startDate, endDate);
            summary.put("totalDefenses", totalDefenses);
            
            return ApiResponse.success(summary);
        } catch (Exception e) {
            log.error("获取报表统计汇总失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取流量趋势数据
     */
    @GetMapping("/traffic-trend")
    public ApiResponse<List<Map<String, Object>>> getTrafficTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<Map<String, Object>> data = dashboardStatService.getTrafficTrend(startDate, endDate);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取流量趋势数据失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取攻击趋势数据
     */
    @GetMapping("/attack-trend")
    public ApiResponse<List<Map<String, Object>>> getAttackTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<Map<String, Object>> data = dashboardStatService.getAttackTrend(startDate, endDate);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取攻击趋势数据失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取攻击类型分布数据
     */
    @GetMapping("/attack-type")
    public ApiResponse<List<Map<String, Object>>> getAttackTypeDistribution() {
        try {
            List<Map<String, Object>> data = dashboardStatService.getAttackTypeDistribution();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取攻击类型分布失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取风险等级分布数据
     */
    @GetMapping("/risk-level")
    public ApiResponse<List<Map<String, Object>>> getRiskLevelDistribution() {
        try {
            List<Map<String, Object>> data = dashboardStatService.getRiskLevelDistribution();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取风险等级分布失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取攻击源 IP 统计（Top 10）
     */
    @GetMapping("/top-attackers")
    public ApiResponse<List<Map<String, Object>>> getTopAttackers() {
        try {
            List<Map<String, Object>> data = dashboardStatService.getTopSourceIps();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取攻击源 IP 统计失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }
}
