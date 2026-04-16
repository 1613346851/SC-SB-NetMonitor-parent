package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/defense")
public class DefenseLogController {

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime startTime = parseDateTime(startDate);
            LocalDateTime endTime = parseDateTime(endDate);

            Map<String, Object> stats = new HashMap<>();
            
            long totalCount = defenseLogMapper.countByCondition(null, null, null, null, startTime, endTime);
            stats.put("totalDefenses", totalCount);
            stats.put("totalCount", totalCount);
            
            long alertOnlyCount = defenseLogMapper.countByCondition(null, "ALERT_ONLY", null, null, startTime, endTime);
            long actualDefenseCount = totalCount - alertOnlyCount;
            stats.put("actualDefenseCount", actualDefenseCount);
            
            long successCount = defenseLogMapper.countByCondition(null, null, null, 1, startTime, endTime);
            long alertOnlySuccessCount = defenseLogMapper.countByCondition(null, "ALERT_ONLY", null, 1, startTime, endTime);
            long actualSuccessCount = successCount - alertOnlySuccessCount;
            stats.put("successDefenses", actualSuccessCount);
            stats.put("successCount", actualSuccessCount);
            
            long failedCount = defenseLogMapper.countByCondition(null, null, null, 0, startTime, endTime);
            stats.put("failedDefenses", failedCount);
            stats.put("failedCount", failedCount);
            
            double successRate = actualDefenseCount > 0 ? (double) actualSuccessCount / actualDefenseCount * 100 : 0;
            stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            long blockIpCount = defenseLogMapper.countByCondition(null, "BLOCK_IP", null, null, startTime, endTime);
            long addBlacklistCount = defenseLogMapper.countByCondition(null, "ADD_BLACKLIST", null, null, startTime, endTime);
            stats.put("blockCount", blockIpCount + addBlacklistCount);
            
            long rateLimitCount = defenseLogMapper.countByCondition(null, "RATE_LIMIT", null, null, startTime, endTime);
            stats.put("rateLimitCount", rateLimitCount);
            
            long blockRequestCount = defenseLogMapper.countByCondition(null, "BLOCK_REQUEST", null, null, startTime, endTime);
            stats.put("redirectCount", blockRequestCount);
            
            stats.put("alertOnlyCount", alertOnlyCount);

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取防御统计失败：", e);
            return ApiResponse.error("获取统计失败");
        }
    }

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> getTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime startTime = parseDateTime(startDate);
            LocalDateTime endTime = parseDateTime(endDate);

            if (startTime == null) {
                startTime = LocalDateTime.now().minusDays(7);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            List<DefenseLogEntity> allLogs = defenseLogMapper.selectByCondition(
                null, null, null, null, startTime, endTime, 0, Integer.MAX_VALUE, null, null
            );
            
            Map<String, Map<String, Long>> dailyStats = new LinkedHashMap<>();
            
            for (DefenseLogEntity log : allLogs) {
                if (log.getExecuteTime() != null) {
                    String dateKey = log.getExecuteTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    
                    dailyStats.computeIfAbsent(dateKey, k -> {
                        Map<String, Long> stats = new HashMap<>();
                        stats.put("success", 0L);
                        stats.put("fail", 0L);
                        stats.put("alert", 0L);
                        return stats;
                    });
                    
                    Map<String, Long> stats = dailyStats.get(dateKey);
                    if ("ALERT_ONLY".equals(log.getDefenseType())) {
                        stats.put("alert", stats.get("alert") + 1);
                    } else if (log.getExecuteStatus() != null && log.getExecuteStatus() == 1) {
                        stats.put("success", stats.get("success") + 1);
                    } else {
                        stats.put("fail", stats.get("fail") + 1);
                    }
                }
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MM-dd");
            for (Map.Entry<String, Map<String, Long>> entry : dailyStats.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", entry.getKey().substring(5));
                item.put("success", entry.getValue().get("success"));
                item.put("fail", entry.getValue().get("fail"));
                item.put("alert", entry.getValue().get("alert"));
                result.add(item);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取防御趋势失败：", e);
            return ApiResponse.error("获取趋势失败");
        }
    }

    @GetMapping("/success-rate-by-type")
    public ApiResponse<List<Map<String, Object>>> getSuccessRateByType(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime startTime = parseDateTime(startDate);
            LocalDateTime endTime = parseDateTime(endDate);

            List<Map<String, Object>> result = new ArrayList<>();
            
            Map<String, String> typeNameMap = new LinkedHashMap<>();
            typeNameMap.put("BLOCK_IP", "IP封禁");
            typeNameMap.put("RATE_LIMIT", "限流");
            typeNameMap.put("BLOCK_REQUEST", "请求拦截");
            typeNameMap.put("ALERT_ONLY", "仅告警");
            
            for (Map.Entry<String, String> entry : typeNameMap.entrySet()) {
                String typeCode = entry.getKey();
                String typeName = entry.getValue();
                
                long total = defenseLogMapper.countByCondition(null, typeCode, null, null, startTime, endTime);
                long success = defenseLogMapper.countByCondition(null, typeCode, null, 1, startTime, endTime);
                
                double rate = total > 0 ? Math.round((double) success / total * 10000.0) / 100.0 : 0;
                
                Map<String, Object> item = new HashMap<>();
                item.put("type", typeName);
                item.put("rate", rate);
                item.put("total", total);
                item.put("success", success);
                result.add(item);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取各类型成功率失败：", e);
            return ApiResponse.error("获取数据失败");
        }
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getDefenseLogs(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String defenseType,
            @RequestParam(required = false) String defenseTarget,
            @RequestParam(required = false) Integer executeStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {

        try {
            if (pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }
            
            int offset = (pageNum - 1) * pageSize;

            LocalDateTime startTime = null;
            LocalDateTime endTime = null;
            if (startDate != null && !startDate.isEmpty()) {
                startTime = LocalDateTime.parse(startDate.replace(" ", "T").substring(0, 19));
            }
            if (endDate != null && !endDate.isEmpty()) {
                endTime = LocalDateTime.parse(endDate.replace(" ", "T").substring(0, 19));
            }

            List<DefenseLogEntity> list = defenseLogMapper.selectByCondition(
                eventId, defenseType, defenseTarget, executeStatus, startTime, endTime, offset, pageSize, sortField, sortOrder
            );

            long total = defenseLogMapper.countByCondition(
                eventId, defenseType, defenseTarget, executeStatus, startTime, endTime
            );

            List<Map<String, Object>> resultList = new java.util.ArrayList<>();
            for (DefenseLogEntity entity : list) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", entity.getId());
                item.put("defenseType", entity.getDefenseType());
                item.put("defenseAction", entity.getDefenseAction());
                item.put("defenseTarget", entity.getDefenseTarget());
                item.put("eventId", entity.getEventId());
                item.put("isFirst", entity.getIsFirst());
                item.put("attackId", entity.getAttackId());
                item.put("trafficId", entity.getTrafficId());
                item.put("ruleId", entity.getRuleId());
                item.put("defenseReason", entity.getDefenseReason());
                item.put("expireTime", entity.getExpireTime() != null ? 
                    entity.getExpireTime().format(DATE_FORMATTER) : null);
                item.put("executeStatus", entity.getExecuteStatus());
                item.put("executeResult", entity.getExecuteResult());
                item.put("operator", entity.getOperator());
                item.put("createTime", entity.getExecuteTime() != null ? 
                    entity.getExecuteTime().format(DATE_FORMATTER) : null);
                resultList.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("list", resultList);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/by-attack/{attackId}")
    public ApiResponse<List<DefenseLogEntity>> getByAttackId(@PathVariable Long attackId) {
        try {
            List<DefenseLogEntity> list = defenseLogMapper.selectByCondition(
                null, null, null, null, null, null, 0, 100, null, null
            );
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("根据攻击 ID 查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getDefenseLogById(@PathVariable Long id) {
        try {
            DefenseLogEntity entity = defenseLogMapper.selectById(id);
            if (entity == null) {
                return ApiResponse.error("防御日志不存在");
            }
            
            Map<String, Object> item = new HashMap<>();
            item.put("id", entity.getId());
            item.put("defenseType", entity.getDefenseType());
            item.put("defenseAction", entity.getDefenseAction());
            item.put("defenseTarget", entity.getDefenseTarget());
            item.put("eventId", entity.getEventId());
            item.put("isFirst", entity.getIsFirst());
            item.put("attackId", entity.getAttackId());
            item.put("trafficId", entity.getTrafficId());
            item.put("ruleId", entity.getRuleId());
            item.put("defenseReason", entity.getDefenseReason());
            item.put("expireTime", entity.getExpireTime() != null ? 
                entity.getExpireTime().format(DATE_FORMATTER) : null);
            item.put("executeStatus", entity.getExecuteStatus());
            item.put("executeResult", entity.getExecuteResult());
            item.put("operator", entity.getOperator());
            item.put("createTime", entity.getExecuteTime() != null ? 
                entity.getExecuteTime().format(DATE_FORMATTER) : null);
            
            return ApiResponse.success(item);
        } catch (Exception e) {
            log.error("查询防御日志失败：id={}", id, e);
            return ApiResponse.error("查询失败");
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr.replace(" ", "T").substring(0, 19));
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/export-report")
    public void exportReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        try {
            LocalDateTime startTime = parseDateTime(startDate);
            LocalDateTime endTime = parseDateTime(endDate);

            if (startTime == null) {
                startTime = LocalDateTime.now().minusDays(7);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            long totalCount = defenseLogMapper.countByCondition(null, null, null, null, startTime, endTime);
            long alertOnlyCount = defenseLogMapper.countByCondition(null, "ALERT_ONLY", null, null, startTime, endTime);
            long actualDefenseCount = totalCount - alertOnlyCount;
            
            long successCount = defenseLogMapper.countByCondition(null, null, null, 1, startTime, endTime);
            long alertOnlySuccessCount = defenseLogMapper.countByCondition(null, "ALERT_ONLY", null, 1, startTime, endTime);
            long actualSuccessCount = successCount - alertOnlySuccessCount;
            long failedCount = defenseLogMapper.countByCondition(null, null, null, 0, startTime, endTime);
            
            long blockIpCount = defenseLogMapper.countByCondition(null, "BLOCK_IP", null, null, startTime, endTime);
            long addBlacklistCount = defenseLogMapper.countByCondition(null, "ADD_BLACKLIST", null, null, startTime, endTime);
            long rateLimitCount = defenseLogMapper.countByCondition(null, "RATE_LIMIT", null, null, startTime, endTime);
            long blockRequestCount = defenseLogMapper.countByCondition(null, "BLOCK_REQUEST", null, null, startTime, endTime);

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=defense_report_" + 
                LocalDate.now().toString() + ".csv");
            
            PrintWriter writer = response.getWriter();
            writer.println("\uFEFF");
            writer.println("防御效果评估报告");
            writer.println("统计时间范围," + startTime.format(DATE_FORMATTER) + " 至 " + endTime.format(DATE_FORMATTER));
            writer.println();
            writer.println("统计项目,数值");
            writer.println("总防御次数," + actualDefenseCount);
            writer.println("成功防御," + actualSuccessCount);
            writer.println("失败防御," + failedCount);
            double successRate = actualDefenseCount > 0 ? (double) actualSuccessCount / actualDefenseCount * 100 : 0;
            writer.println("成功率," + String.format("%.1f", successRate) + "%");
            writer.println("仅告警次数," + alertOnlyCount);
            writer.println();
            writer.println("防御类型统计,次数");
            writer.println("IP封禁," + (blockIpCount + addBlacklistCount));
            writer.println("限流," + rateLimitCount);
            writer.println("请求拦截," + blockRequestCount);
            writer.println("仅告警," + alertOnlyCount);
            writer.println();
            writer.println("导出时间," + LocalDateTime.now().format(DATE_FORMATTER));
            
            writer.flush();
        } catch (Exception e) {
            log.error("导出防御报告失败：", e);
        }
    }
}
