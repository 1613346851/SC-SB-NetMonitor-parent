package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.PageResult;
import com.network.monitor.entity.AlertEntity;
import com.network.monitor.entity.AlertRuleEntity;
import com.network.monitor.mapper.AlertMapper;
import com.network.monitor.service.AlertService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertMapper alertMapper;
    
    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getAlertList(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        try {
            if (pageNum < 1) pageNum = 1;
            if (pageSize < 1 || pageSize > 100) pageSize = 10;

            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            String orderBy = buildOrderBy(sortField, sortOrder);

            PageResult<AlertEntity> result = alertService.queryAlerts(
                alertLevel, status, sourceIp, attackType, startDateTime, endDateTime,
                pageNum, pageSize, orderBy);

            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getList());
            data.put("total", result.getTotal());
            data.put("pageNum", result.getPage());
            data.put("pageSize", result.getSize());
            data.put("totalPages", result.getTotalPages());

            return ApiResponse.success(data);
        } catch (Exception e) {
            logger.error("查询告警列表失败", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertEntity> getAlertById(@PathVariable Long id) {
        try {
            AlertEntity alert = alertService.getById(id);
            if (alert == null) {
                return ApiResponse.error("告警不存在");
            }
            return ApiResponse.success(alert);
        } catch (Exception e) {
            logger.error("查询告警详情失败: id={}", id, e);
            return ApiResponse.error("查询失败");
        }
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirmAlert(@PathVariable Long id, HttpServletRequest request) {
        try {
            String username = SecurityUtil.getCurrentUsername();
            alertService.confirm(id, username);
            operLogService.logOperation(username, "UPDATE", "告警管理", 
                "确认告警：" + id, "confirm", "/api/alert/" + id + "/confirm", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("确认告警失败: id={}", id, e);
            return ApiResponse.error("确认失败");
        }
    }

    @PostMapping("/{id}/ignore")
    public ApiResponse<Void> ignoreAlert(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        try {
            String username = SecurityUtil.getCurrentUsername();
            String reason = body != null ? body.get("reason") : null;
            alertService.ignore(id, username, reason);
            operLogService.logOperation(username, "UPDATE", "告警管理", 
                "忽略告警：" + id, "ignore", "/api/alert/" + id + "/ignore", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("忽略告警失败: id={}", id, e);
            return ApiResponse.error("操作失败");
        }
    }

    @PostMapping("/batch-confirm")
    public ApiResponse<Void> batchConfirm(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要确认的告警");
            }
            String username = SecurityUtil.getCurrentUsername();
            alertService.batchConfirm(ids, username);
            operLogService.logOperation(username, "UPDATE", "告警管理", 
                "批量确认告警，共" + ids.size() + "条", "batchConfirm", "/api/alert/batch-confirm", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("批量确认告警失败", e);
            return ApiResponse.error("操作失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAlert(@PathVariable Long id, HttpServletRequest request) {
        try {
            alertService.delete(id);
            operLogService.logOperation(SecurityUtil.getCurrentUsername(), "DELETE", "告警管理", 
                "删除告警：" + id, "delete", "/api/alert/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("删除告警失败: id={}", id, e);
            return ApiResponse.error("删除失败");
        }
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDelete(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要删除的告警");
            }
            alertService.batchDelete(ids);
            operLogService.logOperation(SecurityUtil.getCurrentUsername(), "DELETE", "告警管理", 
                "批量删除告警，共" + ids.size() + "条", "batchDelete", "/api/alert/batch", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("批量删除告警失败", e);
            return ApiResponse.error("删除失败");
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getAlertStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("pending", alertService.countPending());
            stats.put("critical", alertService.countByLevel("CRITICAL"));
            stats.put("high", alertService.countByLevel("HIGH"));
            stats.put("medium", alertService.countByLevel("MEDIUM"));
            stats.put("low", alertService.countByLevel("LOW"));
            
            List<AlertMapper.LevelCountStat> levelStats = 
                alertService.countByAlertLevel(LocalDateTime.now().minusDays(7), LocalDateTime.now());
            stats.put("levelStats", levelStats);
            
            List<AlertMapper.StatusCountStat> statusStats = alertService.countByStatus();
            stats.put("statusStats", statusStats);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            logger.error("获取告警统计失败", e);
            return ApiResponse.error("获取统计失败");
        }
    }

    @GetMapping("/pending")
    public ApiResponse<List<AlertEntity>> getPendingAlerts(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<AlertEntity> alerts = alertService.getPendingAlerts(limit);
            return ApiResponse.success(alerts);
        } catch (Exception e) {
            logger.error("获取待处理告警失败", e);
            return ApiResponse.error("获取失败");
        }
    }

    @GetMapping("/rules")
    public ApiResponse<List<AlertRuleEntity>> getAlertRules() {
        try {
            List<AlertRuleEntity> rules = alertService.getAllRules();
            return ApiResponse.success(rules);
        } catch (Exception e) {
            logger.error("获取告警规则失败", e);
            return ApiResponse.error("获取失败");
        }
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Void> updateRule(@PathVariable Long id, @RequestBody AlertRuleEntity rule) {
        try {
            rule.setId(id);
            alertService.updateRule(rule);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("更新告警规则失败: id={}", id, e);
            return ApiResponse.error("更新失败");
        }
    }

    @PostMapping("/rules/{id}/toggle")
    public ApiResponse<Void> toggleRule(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                return ApiResponse.error("缺少enabled参数");
            }
            alertService.toggleRule(id, enabled);
            return ApiResponse.success();
        } catch (Exception e) {
            logger.error("切换告警规则状态失败: id={}", id, e);
            return ApiResponse.error("操作失败");
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
            case "alertLevel": return "alert_level";
            case "sourceIp": return "source_ip";
            case "attackType": return "attack_type";
            case "status": return "status";
            case "createTime": return "create_time";
            case "firstOccurTime": return "first_occur_time";
            default: return "id";
        }
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
