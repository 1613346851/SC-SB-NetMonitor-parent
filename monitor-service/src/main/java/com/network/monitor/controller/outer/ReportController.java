package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.util.CsvFileUtil;
import com.network.monitor.service.DashboardStatService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    
    @Autowired
    private OperLogService operLogService;

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

    @GetMapping("/export")
    public void exportReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Map<String, Object> summary = new HashMap<>();
            summary.put("总流量数", dashboardStatService.getTotalTraffic(startDate, endDate));
            summary.put("总攻击次数", dashboardStatService.getTotalAttacks(startDate, endDate));
            summary.put("总漏洞数", dashboardStatService.getTotalVulnerabilities(startDate, endDate));
            summary.put("总防御次数", dashboardStatService.getTotalDefenses(startDate, endDate));

            List<Map<String, Object>> trafficTrend = dashboardStatService.getTrafficTrend(startDate, endDate);
            List<Map<String, Object>> attackTrend = dashboardStatService.getAttackTrend(startDate, endDate);
            List<Map<String, Object>> attackType = dashboardStatService.getAttackTypeDistribution();
            List<Map<String, Object>> riskLevel = dashboardStatService.getRiskLevelDistribution();
            List<Map<String, Object>> topAttackers = dashboardStatService.getTopSourceIps();

            List<String> headers = List.of(
                    "指标", "数值"
            );

            List<Map<String, Object>> csvData = new ArrayList<>();
            
            csvData.add(createRow("=== 统计摘要 ===", ""));
            csvData.add(createRow("总流量数", summary.get("总流量数")));
            csvData.add(createRow("总攻击次数", summary.get("总攻击次数")));
            csvData.add(createRow("总漏洞数", summary.get("总漏洞数")));
            csvData.add(createRow("总防御次数", summary.get("总防御次数")));
            csvData.add(createRow("", ""));
            
            csvData.add(createRow("=== 流量趋势 ===", ""));
            for (Map<String, Object> item : trafficTrend) {
                csvData.add(createRow(item.get("date"), item.get("count")));
            }
            csvData.add(createRow("", ""));
            
            csvData.add(createRow("=== 攻击趋势 ===", ""));
            for (Map<String, Object> item : attackTrend) {
                csvData.add(createRow(item.get("date"), item.get("count")));
            }
            csvData.add(createRow("", ""));
            
            csvData.add(createRow("=== 攻击类型分布 ===", ""));
            for (Map<String, Object> item : attackType) {
                csvData.add(createRow(item.get("name"), item.get("value")));
            }
            csvData.add(createRow("", ""));
            
            csvData.add(createRow("=== 风险等级分布 ===", ""));
            for (Map<String, Object> item : riskLevel) {
                csvData.add(createRow(item.get("name"), item.get("value")));
            }
            csvData.add(createRow("", ""));
            
            csvData.add(createRow("=== 攻击源 IP Top 10 ===", ""));
            for (Map<String, Object> item : topAttackers) {
                csvData.add(createRow(item.get("ip"), item.get("count")));
            }

            String fileName = CsvFileUtil.generateCsvFileName("data_report");
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", 
                    "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            response.setCharacterEncoding("UTF-8");

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
                CsvFileUtil.writeCsv(writer, headers, csvData, true);
            }

            operLogService.logOperation(SecurityUtil.getCurrentUsername(), "EXPORT", "数据报表", 
                "导出数据报表", "export", "/api/report/export", getClientIp(request), 0);

            log.info("导出数据报表 CSV 成功");
        } catch (Exception e) {
            log.error("导出数据报表 CSV 失败：", e);
        }
    }

    private Map<String, Object> createRow(Object col1, Object col2) {
        Map<String, Object> row = new HashMap<>();
        row.put("指标", col1 != null ? col1.toString() : "");
        row.put("数值", col2 != null ? col2.toString() : "");
        return row;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
