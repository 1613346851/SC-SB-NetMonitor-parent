package com.network.monitor.controller.outer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 攻击监测控制器（对外前端业务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/attack")
public class AttackMonitorController {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysConfigCache sysConfigCache;

    /**
     * 获取风险等级选项列表
     */
    @GetMapping("/risk-levels")
    public ApiResponse<List<Map<String, String>>> getRiskLevels() {
        try {
            List<Map<String, String>> riskLevels = List.of(
                Map.of("value", "CRITICAL", "label", "严重"),
                Map.of("value", "HIGH", "label", "高风险"),
                Map.of("value", "MEDIUM", "label", "中风险"),
                Map.of("value", "LOW", "label", "低风险")
            );
            return ApiResponse.success(riskLevels);
        } catch (Exception e) {
            log.error("获取风险等级列表失败：", e);
            return ApiResponse.error("获取失败");
        }
    }

    /**
     * 分页查询攻击记录
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getAttackList(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer handled,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        try {
            if (pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }
            
            int offset = (pageNum - 1) * pageSize;
            
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            String orderBy = buildOrderBy(sortField, sortOrder);
            
            List<AttackMonitorEntity> list = attackMonitorMapper.selectByCondition(
                eventId, attackType, riskLevel, sourceIp, handled, startDateTime, endDateTime, offset, pageSize, orderBy
            );
            
            long total = attackMonitorMapper.countByCondition(
                eventId, attackType, riskLevel, sourceIp, handled, startDateTime, endDateTime
            );

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询攻击记录失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 查询未处理的高危告警
     */
    @GetMapping("/unhandled/high-risk")
    public ApiResponse<List<AttackMonitorEntity>> getUnhandledHighRisk() {
        try {
            List<AttackMonitorEntity> list = attackMonitorMapper.selectUnHandledHighRisk();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询未处理高危告警失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<AttackMonitorEntity> getAttackById(@PathVariable Long id) {
        try {
            AttackMonitorEntity entity = attackMonitorMapper.selectById(id);
            if (entity == null) {
                return ApiResponse.error("攻击记录不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询攻击记录失败：id={}", id, e);
            return ApiResponse.error("查询失败");
        }
    }

    @PutMapping("/{id}/handle")
    public ApiResponse<Void> handleAttack(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String remark = body != null ? (String) body.get("handleRemark") : "已手动处理";
            attackMonitorMapper.updateHandled(id, 1, remark);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理攻击记录失败：", e);
            return ApiResponse.error("处理失败");
        }
    }

    /**
     * 导出攻击数据为CSV
     */
    @GetMapping("/export")
    public void exportAttack(
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer handled,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            HttpServletResponse response) {
        
        try {
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            String orderBy = buildOrderBy(sortField, sortOrder);
            
            List<AttackMonitorEntity> list = attackMonitorMapper.selectByCondition(
                null, attackType, riskLevel, sourceIp, handled, startDateTime, endDateTime, 0, 10000, orderBy
            );
            
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=attack_export.csv");
            
            PrintWriter writer = response.getWriter();
            writer.write("\uFEFF");
            writer.println("ID,攻击时间,源IP,目标URI,攻击类型,风险等级,置信度,处理状态");
            
            for (AttackMonitorEntity item : list) {
                writer.println(String.format("%d,%s,%s,%s,%s,%s,%s%%,%s",
                    item.getId(),
                    item.getCreateTime(),
                    escapeCsv(item.getSourceIp()),
                    escapeCsv(item.getTargetUri()),
                    escapeCsv(item.getAttackType()),
                    escapeCsv(item.getRiskLevel()),
                    item.getConfidence() != null ? item.getConfidence() : 0,
                    item.getHandled() == 1 ? "已处理" : "未处理"
                ));
            }
            
            writer.flush();
        } catch (IOException e) {
            log.error("导出攻击数据失败：", e);
        }
    }

    /**
     * 准实时告警接口（SSE）
     * 使用长轮询方式推送未处理的高危告警
     */
    @GetMapping("/alert/realtime")
    public void getRealTimeAlerts(
            @RequestParam(required = false, defaultValue = "30000") Long timeout,
            HttpServletResponse response) {
        
        try {
            boolean alertEnabled = sysConfigCache.getBooleanValue("alert.enabled", true);
            if (!alertEnabled) {
                response.getWriter().write("data: []\n\n");
                response.getWriter().write(": alert disabled\n\n");
                response.getWriter().flush();
                return;
            }

            long pushInterval = sysConfigCache.getLongValue("alert.push.interval", 5000);
            long heartbeatInterval = sysConfigCache.getLongValue("alert.heartbeat.interval", 10000);

            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < timeout) {
                List<AttackMonitorEntity> alerts = attackMonitorMapper.selectUnHandledHighRisk();
                
                if (alerts != null && !alerts.isEmpty()) {
                    response.getWriter().write("data: " + toJson(alerts) + "\n\n");
                    response.getWriter().flush();
                    Thread.sleep(pushInterval);
                } else {
                    response.getWriter().write(": heartbeat\n\n");
                    response.getWriter().flush();
                    Thread.sleep(heartbeatInterval);
                }
                
                if (response.isCommitted()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("准实时告警推送失败：", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败：", e);
            return "{}";
        }
    }

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

    private String buildOrderBy(String sortField, String sortOrder) {
        String field = mapSortField(sortField);
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return field + " " + order;
    }

    private String mapSortField(String sortField) {
        switch (sortField) {
            case "id": return "id";
            case "createTime": return "create_time";
            case "sourceIp": return "source_ip";
            case "riskLevel": return "risk_level";
            case "confidence": return "confidence";
            default: return "id";
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
