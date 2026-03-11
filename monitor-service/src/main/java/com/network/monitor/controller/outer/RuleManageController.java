package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 查询所有规则
     */
    @GetMapping("/list")
    public ApiResponse<List<MonitorRuleEntity>> getAllRules() {
        try {
            List<MonitorRuleEntity> list = monitorRuleMapper.selectAll();
            return ApiResponse.success(list);
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
    public ApiResponse<Void> addRule(@RequestBody MonitorRuleEntity rule) {
        try {
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            if (rule.getEnabled() == null) {
                rule.setEnabled(1);
            }
            monitorRuleMapper.insert(rule);
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
    public ApiResponse<Void> updateRule(@RequestBody MonitorRuleEntity rule) {
        try {
            rule.setUpdateTime(LocalDateTime.now());
            monitorRuleMapper.update(rule);
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
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        try {
            monitorRuleMapper.deleteById(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除规则失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    /**
     * 切换规则启用状态
     */
    @PostMapping("/toggle/{id}")
    public ApiResponse<Void> toggleRule(@PathVariable Long id) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(id);
            if (rule == null) {
                return ApiResponse.notFound("规则不存在");
            }
            int newEnabled = rule.getEnabled() == 1 ? 0 : 1;
            monitorRuleMapper.updateEnabled(id, newEnabled);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("切换规则状态失败：", e);
            return ApiResponse.error("操作失败");
        }
    }
}
