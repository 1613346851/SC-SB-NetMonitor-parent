package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.util.CsvFileUtil;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.TrafficMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导出控制器（CSV 导出）
 * 基于 HttpServletResponse 实现 CSV 文件流输出，支持浏览器直接下载筛选后的监测数据
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
public class DataExportController {

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    /**
     * 导出流量数据为 CSV
     */
    @GetMapping("/traffic")
    public void exportTraffic(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) {

        try {
            // 查询数据
            List<TrafficMonitorEntity> dataList = queryTrafficData(sourceIp, requestUri, startTime, endTime);

            // 准备表头
            List<String> headers = List.of(
                    "ID", "源 IP", "目标 IP", "源端口", "目标端口",
                    "请求方法", "请求 URI", "查询参数", "协议", "用户代理",
                    "请求时间", "创建时间"
            );

            // 转换为 Map 列表
            List<Map<String, Object>> csvData = convertTrafficToCsv(dataList);

            // 导出 CSV
            exportCsv(response, "traffic_data", headers, csvData);

            log.info("导出流量数据 CSV 成功，记录数：{}", dataList.size());
        } catch (Exception e) {
            log.error("导出流量数据 CSV 失败：", e);
        }
    }

    /**
     * 导出攻击数据为 CSV
     */
    @GetMapping("/attack")
    public void exportAttack(
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer handled,
            HttpServletResponse response) {

        try {
            // 查询数据
            List<AttackMonitorEntity> dataList = queryAttackData(attackType, riskLevel, sourceIp, handled);

            // 准备表头
            List<String> headers = List.of(
                    "ID", "流量 ID", "攻击类型", "风险等级", "置信度",
                    "规则 ID", "规则内容", "源 IP", "目标 URI",
                    "攻击内容", "是否已处理", "处理时间", "处理备注", "创建时间"
            );

            // 转换为 Map 列表
            List<Map<String, Object>> csvData = convertAttackToCsv(dataList);

            // 导出 CSV
            exportCsv(response, "attack_data", headers, csvData);

            log.info("导出攻击数据 CSV 成功，记录数：{}", dataList.size());
        } catch (Exception e) {
            log.error("导出攻击数据 CSV 失败：", e);
        }
    }

    /**
     * 查询流量数据
     */
    private List<TrafficMonitorEntity> queryTrafficData(String sourceIp, String requestUri, 
                                                         String startTime, String endTime) {
        LocalDateTime startDateTime = parseDateTime(startTime);
        LocalDateTime endDateTime = parseDateTime(endTime);

        // 查询前 10000 条数据
        return trafficMonitorMapper.selectByCondition(
                sourceIp, requestUri, startDateTime, endDateTime, 0, 10000
        );
    }

    /**
     * 查询攻击数据
     */
    private List<AttackMonitorEntity> queryAttackData(String attackType, String riskLevel, 
                                                       String sourceIp, Integer handled) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // 查询前 10000 条数据
        return attackMonitorMapper.selectByCondition(
                attackType, riskLevel, sourceIp, handled, thirtyDaysAgo, now, 0, 10000
        );
    }

    /**
     * 转换流量数据为 CSV 格式
     */
    private List<Map<String, Object>> convertTrafficToCsv(List<TrafficMonitorEntity> dataList) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (TrafficMonitorEntity entity : dataList) {
            Map<String, Object> row = new HashMap<>();
            row.put("ID", entity.getId());
            row.put("源 IP", entity.getSourceIp());
            row.put("目标 IP", entity.getTargetIp());
            row.put("源端口", entity.getSourcePort());
            row.put("目标端口", entity.getTargetPort());
            row.put("请求方法", entity.getHttpMethod());
            row.put("请求 URI", entity.getRequestUri());
            row.put("查询参数", entity.getQueryParams());
            row.put("协议", entity.getProtocol() != null ? entity.getProtocol() : "HTTP/1.1");
            row.put("用户代理", entity.getUserAgent());
            row.put("请求时间", entity.getRequestTime());
            row.put("创建时间", entity.getCreateTime());
            result.add(row);
        }

        return result;
    }

    /**
     * 转换攻击数据为 CSV 格式
     */
    private List<Map<String, Object>> convertAttackToCsv(List<AttackMonitorEntity> dataList) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (AttackMonitorEntity entity : dataList) {
            Map<String, Object> row = new HashMap<>();
            row.put("ID", entity.getId());
            row.put("流量 ID", entity.getTrafficId());
            row.put("攻击类型", entity.getAttackType());
            row.put("风险等级", entity.getRiskLevel());
            row.put("置信度", entity.getConfidence());
            row.put("规则 ID", entity.getRuleId());
            row.put("规则内容", entity.getRuleContent());
            row.put("源 IP", entity.getSourceIp());
            row.put("目标 URI", entity.getTargetUri());
            row.put("攻击内容", entity.getAttackContent());
            row.put("是否已处理", entity.getHandled() == 1 ? "是" : "否");
            row.put("处理时间", entity.getHandleTime());
            row.put("处理备注", entity.getHandleRemark());
            row.put("创建时间", entity.getCreateTime());
            result.add(row);
        }

        return result;
    }

    /**
     * 导出 CSV 文件
     */
    private void exportCsv(HttpServletResponse response, String fileNamePrefix, 
                          List<String> headers, List<Map<String, Object>> data) throws IOException {
        // 设置响应头
        String fileName = CsvFileUtil.generateCsvFileName(fileNamePrefix);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", 
                "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
        response.setCharacterEncoding("UTF-8");

        // 写入 CSV 数据
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            CsvFileUtil.writeCsv(writer, headers, data, true);
        }
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
