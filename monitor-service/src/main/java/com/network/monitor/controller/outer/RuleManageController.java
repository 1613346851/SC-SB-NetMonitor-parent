package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则管理控制器（对外前端业务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/rule")
public class RuleManageController {

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    /**
     * 根据 ID 查询规则
     */
    @GetMapping("/{id}")
    public ApiResponse<MonitorRuleEntity> getRuleById(@PathVariable Long id) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            return ApiResponse.success(rule);
        } catch (Exception e) {
            log.error("查询规则详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 分页查询规则列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRuleList(
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            int offset = (page - 1) * size;
            
            List<MonitorRuleEntity> list = monitorRuleMapper.selectByCondition(
                ruleName, attackType, enabled, offset, size
            );
            
            long total = monitorRuleMapper.countByCondition(ruleName, attackType, enabled);

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询规则列表失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 查询启用的规则
     */
    @GetMapping("/enabled")
    public ApiResponse<List<MonitorRuleEntity>> getEnabledRules() {
        try {
            List<MonitorRuleEntity> list = monitorRuleMapper.selectAllEnabled();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询启用规则失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 新增规则
     */
    @PostMapping("/add")
    public ApiResponse<Void> addRule(@RequestBody MonitorRuleEntity rule, HttpServletRequest request) {
        try {
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            if (rule.getEnabled() == null) {
                rule.setEnabled(1);
            }
            monitorRuleMapper.insert(rule);
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "规则管理", 
                "新增规则：" + rule.getRuleName(), "add", "/api/rule/add", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("新增规则失败：", e);
            return ApiResponse.error("新增失败");
        }
    }

    /**
     * 更新规则
     */
    @PostMapping("/update")
    public ApiResponse<Void> updateRule(@RequestBody MonitorRuleEntity rule, HttpServletRequest request) {
        try {
            rule.setUpdateTime(LocalDateTime.now());
            monitorRuleMapper.update(rule);
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理", 
                "更新规则ID：" + rule.getId(), "update", "/api/rule/update", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新规则失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id, HttpServletRequest request) {
        try {
            monitorRuleMapper.deleteById(id);
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "规则管理", 
                "删除规则ID：" + id, "delete", "/api/rule/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除规则失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    /**
     * 切换规则启用状态
     */
    @PutMapping("/{id}/toggle")
    public ApiResponse<Void> toggleRule(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            int newEnabled = rule.getEnabled() == 1 ? 0 : 1;
            monitorRuleMapper.updateEnabled(id, newEnabled);
            String action = newEnabled == 1 ? "启用" : "禁用";
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理", 
                action + "规则ID：" + id, "toggle", "/api/rule/" + id + "/toggle", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("切换规则状态失败：", e);
            return ApiResponse.error("操作失败");
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
