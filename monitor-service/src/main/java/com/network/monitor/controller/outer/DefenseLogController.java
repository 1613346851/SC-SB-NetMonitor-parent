package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
            
            long successCount = defenseLogMapper.countByCondition(null, null, null, 1, startTime, endTime);
            stats.put("successDefenses", successCount);
            stats.put("successCount", successCount);
            
            long failedCount = defenseLogMapper.countByCondition(null, null, null, 0, startTime, endTime);
            stats.put("failedDefenses", failedCount);
            stats.put("failedCount", failedCount);
            
            double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
            stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            long blockIpCount = defenseLogMapper.countByCondition(null, "BLOCK_IP", null, null, startTime, endTime);
            stats.put("blockCount", blockIpCount);
            
            long rateLimitCount = defenseLogMapper.countByCondition(null, "RATE_LIMIT", null, null, startTime, endTime);
            stats.put("rateLimitCount", rateLimitCount);
            
            long blockRequestCount = defenseLogMapper.countByCondition(null, "BLOCK_REQUEST", null, null, startTime, endTime);
            stats.put("redirectCount", blockRequestCount);

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
                        return stats;
                    });
                    
                    Map<String, Long> stats = dailyStats.get(dateKey);
                    if (log.getExecuteStatus() != null && log.getExecuteStatus() == 1) {
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

    private long countFirstDefense(LocalDateTime startTime, LocalDateTime endTime) {
        List<DefenseLogEntity> logs = defenseLogMapper.selectByCondition(
            null, null, null, null, startTime, endTime, 0, Integer.MAX_VALUE, null, null
        );
        return logs.stream().filter(log -> log.getIsFirst() != null && log.getIsFirst() == 1).count();
    }
}
