package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.service.DashboardStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardStatService dashboardStatService;

    /**
     * 获取仪表盘核心统计指标
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = dashboardStatService.getDashboardStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取仪表盘数据失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取攻击类型分布
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
     * 获取流量趋势
     */
    @GetMapping("/traffic-trend")
    public ApiResponse<List<Map<String, Object>>> getTrafficTrend(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            List<Map<String, Object>> data = dashboardStatService.getTrafficTrend(startTime, endTime);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取流量趋势失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    /**
     * 获取漏洞等级分布
     */
    @GetMapping("/vulnerability-level")
    public ApiResponse<List<Map<String, Object>>> getVulnerabilityLevelDistribution() {
        try {
            List<Map<String, Object>> data = dashboardStatService.getVulnerabilityLevelDistribution();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取漏洞等级分布失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }
}
