package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.constant.DefenseRuleStatusConstant;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.entity.VulnerabilityRuleEntity;
import com.network.monitor.mapper.InterfaceRuleMapper;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.mapper.ScanInterfaceMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.mapper.VulnerabilityRuleMapper;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.RuleSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private RuleSyncService ruleSyncService;

    @Autowired
    private InterfaceRuleMapper interfaceRuleMapper;

    @Autowired
    private VulnerabilityRuleMapper vulnerabilityRuleMapper;

    @Autowired
    private ScanInterfaceMapper scanInterfaceMapper;

    @Autowired
    private VulnerabilityMonitorMapper vulnerabilityMonitorMapper;

    /**
     * 根据 ID 查询规则（包含关联漏洞信息）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getRuleById(@PathVariable Long id) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            
            List<VulnerabilityRuleEntity> vulnRules = vulnerabilityRuleMapper.selectByRuleId(id);
            List<Long> vulnIds = vulnRules.stream()
                    .map(VulnerabilityRuleEntity::getVulnerabilityId)
                    .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("rule", rule);
            result.put("vulnerabilityIds", vulnIds);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询规则详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 获取可关联的漏洞列表（用于下拉选择）
     */
    @GetMapping("/available-vulnerabilities")
    public ApiResponse<List<Map<String, Object>>> getAvailableVulnerabilities(
            @RequestParam(required = false) String keyword) {
        try {
            List<VulnerabilityMonitorEntity> vulns;
            if (keyword != null && !keyword.trim().isEmpty()) {
                vulns = vulnerabilityMonitorMapper.selectByKeyword(keyword.trim());
            } else {
                vulns = vulnerabilityMonitorMapper.selectAll();
            }
            
            List<Map<String, Object>> result = vulns.stream().map(vuln -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", vuln.getId());
                map.put("vulnName", vuln.getVulnName());
                map.put("vulnType", vuln.getVulnType());
                map.put("vulnLevel", vuln.getVulnLevel());
                map.put("vulnPath", vuln.getVulnPath());
                return map;
            }).collect(Collectors.toList());
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取漏洞列表失败：", e);
            return ApiResponse.error("获取失败");
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
            
            List<MonitorRuleEntity> list = monitorRuleMapper.selectByConditionWithSort(
                ruleName, attackType, enabled, offset, pageSize, sortField, sortOrder
            );
            
            long total = monitorRuleMapper.countByCondition(ruleName, attackType, enabled);

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

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
    @Transactional
    public ApiResponse<Void> addRule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MonitorRuleEntity rule = new MonitorRuleEntity();
            rule.setRuleName((String) body.get("ruleName"));
            rule.setAttackType((String) body.get("attackType"));
            rule.setRuleContent((String) body.get("ruleContent"));
            rule.setDescription((String) body.get("description"));
            rule.setRiskLevel((String) body.get("riskLevel"));
            rule.setPriority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 100);
            rule.setEnabled(body.get("enabled") != null ? ((Number) body.get("enabled")).intValue() : 1);
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            
            monitorRuleMapper.insert(rule);
            
            List<Integer> vulnIds = (List<Integer>) body.get("vulnerabilityIds");
            if (vulnIds != null && !vulnIds.isEmpty()) {
                for (Integer vulnId : vulnIds) {
                    VulnerabilityRuleEntity vr = new VulnerabilityRuleEntity();
                    vr.setVulnerabilityId(vulnId.longValue());
                    vr.setRuleId(rule.getId());
                    vr.setRuleName(rule.getRuleName());
                    vr.setAttackType(rule.getAttackType());
                    vr.setRiskLevel(rule.getRiskLevel());
                    vulnerabilityRuleMapper.insert(vr);
                    
                    updateVulnerabilityDefenseStatus(vulnId.longValue());
                }
            }
            
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "规则管理", 
                "新增规则：" + rule.getRuleName(), "add", "/api/rule/add", getClientIp(request), 0);
            
            ruleSyncService.syncRuleToGatewayAsync(rule, "ADD");
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("新增规则失败：", e);
            return ApiResponse.error("新增失败");
        }
    }

    /**
     * 更新规则
     */
    @PutMapping("/update")
    @Transactional
    public ApiResponse<Void> updateRule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long ruleId = ((Number) body.get("id")).longValue();
            MonitorRuleEntity existingRule = monitorRuleMapper.selectById(ruleId);
            if (existingRule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            
            MonitorRuleEntity rule = new MonitorRuleEntity();
            rule.setId(ruleId);
            rule.setRuleName((String) body.get("ruleName"));
            rule.setAttackType((String) body.get("attackType"));
            rule.setRuleContent((String) body.get("ruleContent"));
            rule.setDescription((String) body.get("description"));
            rule.setRiskLevel((String) body.get("riskLevel"));
            rule.setPriority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 100);
            rule.setEnabled(body.get("enabled") != null ? ((Number) body.get("enabled")).intValue() : 1);
            rule.setCreateTime(existingRule.getCreateTime());
            rule.setUpdateTime(LocalDateTime.now());
            
            monitorRuleMapper.update(rule);
            
            List<Long> oldVulnIds = vulnerabilityRuleMapper.selectVulnerabilityIdsByRuleId(ruleId);
            
            vulnerabilityRuleMapper.deleteByRuleId(ruleId);
            
            List<Integer> newVulnIds = (List<Integer>) body.get("vulnerabilityIds");
            if (newVulnIds != null && !newVulnIds.isEmpty()) {
                for (Integer vulnId : newVulnIds) {
                    VulnerabilityRuleEntity vr = new VulnerabilityRuleEntity();
                    vr.setVulnerabilityId(vulnId.longValue());
                    vr.setRuleId(ruleId);
                    vr.setRuleName(rule.getRuleName());
                    vr.setAttackType(rule.getAttackType());
                    vr.setRiskLevel(rule.getRiskLevel());
                    vulnerabilityRuleMapper.insert(vr);
                }
            }
            
            for (Long vulnId : oldVulnIds) {
                updateVulnerabilityDefenseStatus(vulnId);
            }
            if (newVulnIds != null) {
                for (Integer vulnId : newVulnIds) {
                    updateVulnerabilityDefenseStatus(vulnId.longValue());
                }
            }
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理", 
                "更新规则ID：" + rule.getId(), "update", "/api/rule/update", getClientIp(request), 0);
            
            ruleSyncService.syncRuleToGatewayAsync(rule, "UPDATE");
            
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
    @Transactional
    public ApiResponse<Void> deleteRule(@PathVariable Long id, HttpServletRequest request) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }

            List<Long> interfaceIds = interfaceRuleMapper.selectInterfaceIdsByRuleId(id);
            List<Long> vulnerabilityIds = vulnerabilityRuleMapper.selectVulnerabilityIdsByRuleId(id);

            interfaceRuleMapper.deleteByRuleId(id);
            vulnerabilityRuleMapper.deleteByRuleId(id);

            monitorRuleMapper.deleteById(id);
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "规则管理", 
                "删除规则ID：" + id, "delete", "/api/rule/" + id, getClientIp(request), 0);
            
            ruleSyncService.syncRuleDeleteToGatewayAsync(id);

            for (Long interfaceId : interfaceIds) {
                updateInterfaceDefenseStatus(interfaceId);
            }

            for (Long vulnerabilityId : vulnerabilityIds) {
                updateVulnerabilityDefenseStatus(vulnerabilityId);
            }
            
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
            
            rule.setEnabled(newEnabled);
            ruleSyncService.syncRuleToGatewayAsync(rule, "UPDATE");
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("切换规则状态失败：", e);
            return ApiResponse.error("操作失败");
        }
    }

    /**
     * 更新规则启用状态
     */
    @PutMapping("/status/{id}")
    public ApiResponse<Void> updateRuleStatus(@PathVariable Long id, @RequestParam Integer enabled, HttpServletRequest request) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            monitorRuleMapper.updateEnabled(id, enabled);
            String action = enabled == 1 ? "启用" : "禁用";
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理", 
                action + "规则ID：" + id, "status", "/api/rule/status/" + id, getClientIp(request), 0);
            
            rule.setEnabled(enabled);
            ruleSyncService.syncRuleToGatewayAsync(rule, "UPDATE");
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新规则状态失败：", e);
            return ApiResponse.error("操作失败");
        }
    }
    
    /**
     * 手动同步所有规则到网关
     */
    @PostMapping("/sync")
    public ApiResponse<Void> syncAllRules(HttpServletRequest request) {
        try {
            boolean success = ruleSyncService.syncAllRulesToGateway();
            if (success) {
                operLogService.logOperation(authService.getCurrentUsername(), "SYNC", "规则管理", 
                    "手动同步所有规则到网关", "sync", "/api/rule/sync", getClientIp(request), 0);
                return ApiResponse.success();
            } else {
                return ApiResponse.error("同步失败");
            }
        } catch (Exception e) {
            log.error("同步规则失败：", e);
            return ApiResponse.error("同步失败");
        }
    }

    /**
     * 批量启用规则
     */
    @PutMapping("/batch-enable")
    public ApiResponse<Void> batchEnableRules(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要启用的规则");
            }
            for (Long id : ids) {
                monitorRuleMapper.updateEnabled(id, 1);
                MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
                if (rule != null) {
                    rule.setEnabled(1);
                    ruleSyncService.syncRuleToGatewayAsync(rule, "UPDATE");
                }
            }
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理",
                "批量启用规则，共" + ids.size() + "条", "batchEnable", "/api/rule/batch-enable", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量启用规则失败：", e);
            return ApiResponse.error("批量启用失败");
        }
    }

    /**
     * 批量禁用规则
     */
    @PutMapping("/batch-disable")
    public ApiResponse<Void> batchDisableRules(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要禁用的规则");
            }
            for (Long id : ids) {
                monitorRuleMapper.updateEnabled(id, 0);
                MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
                if (rule != null) {
                    rule.setEnabled(0);
                    ruleSyncService.syncRuleToGatewayAsync(rule, "UPDATE");
                }
            }
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "规则管理",
                "批量禁用规则，共" + ids.size() + "条", "batchDisable", "/api/rule/batch-disable", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量禁用规则失败：", e);
            return ApiResponse.error("批量禁用失败");
        }
    }

    /**
     * 批量删除规则
     */
    @DeleteMapping("/batch")
    @Transactional
    public ApiResponse<Void> batchDeleteRules(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要删除的规则");
            }
            for (Long id : ids) {
                MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
                if (rule != null) {
                    interfaceRuleMapper.deleteByRuleId(id);
                    vulnerabilityRuleMapper.deleteByRuleId(id);
                    monitorRuleMapper.deleteById(id);
                    ruleSyncService.syncRuleDeleteToGatewayAsync(id);
                }
            }
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "规则管理",
                "批量删除规则，共" + ids.size() + "条", "batchDelete", "/api/rule/batch", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量删除规则失败：", e);
            return ApiResponse.error("批量删除失败");
        }
    }

    /**
     * 更新接口的防御规则状态
     */
    private void updateInterfaceDefenseStatus(Long interfaceId) {
        try {
            int ruleCount = interfaceRuleMapper.countByInterfaceId(interfaceId);
            int status = DefenseRuleStatusConstant.calculateStatus(ruleCount);
            scanInterfaceMapper.updateDefenseRuleStatus(interfaceId, status, ruleCount);
            log.info("更新接口[{}]防御状态: status={}, ruleCount={}", interfaceId, status, ruleCount);
        } catch (Exception e) {
            log.error("更新接口防御状态失败: interfaceId={}", interfaceId, e);
        }
    }

    /**
     * 更新漏洞的防御规则状态
     */
    private void updateVulnerabilityDefenseStatus(Long vulnerabilityId) {
        try {
            List<VulnerabilityRuleEntity> rules = vulnerabilityRuleMapper.selectByVulnerabilityId(vulnerabilityId);
            int ruleCount = rules.size();
            int status = DefenseRuleStatusConstant.calculateStatus(ruleCount);
            String ruleIds = rules.stream()
                    .map(r -> String.valueOf(r.getRuleId()))
                    .collect(Collectors.joining(","));
            vulnerabilityMonitorMapper.updateDefenseStatus(vulnerabilityId, status, ruleCount, ruleIds);
            log.info("更新漏洞[{}]防御状态: status={}, ruleCount={}", vulnerabilityId, status, ruleCount);
        } catch (Exception e) {
            log.error("更新漏洞防御状态失败: vulnerabilityId={}", vulnerabilityId, e);
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
