package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.WhitelistEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.WhitelistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whitelist")
public class WhitelistController {

    @Autowired
    private WhitelistService whitelistService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    @GetMapping("/{id}")
    public ApiResponse<WhitelistEntity> getById(@PathVariable Long id) {
        try {
            WhitelistEntity entity = whitelistService.getById(id);
            if (entity == null) {
                return ApiResponse.notFound("白名单不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询白名单详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getList(
            @RequestParam(required = false) String whitelistType,
            @RequestParam(required = false) String whitelistValue,
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
            
            List<WhitelistEntity> list = whitelistService.getByConditionWithSort(whitelistType, whitelistValue, enabled, offset, pageSize, sortField, sortOrder);
            long total = whitelistService.countByCondition(whitelistType, whitelistValue, enabled);

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询白名单列表失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/enabled")
    public ApiResponse<List<WhitelistEntity>> getEnabled() {
        try {
            List<WhitelistEntity> list = whitelistService.getAllEnabled();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询启用白名单失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<WhitelistEntity>> getByType(@PathVariable String type) {
        try {
            List<WhitelistEntity> list = whitelistService.getByType(type);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询白名单失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @PostMapping("/add")
    public ApiResponse<WhitelistEntity> add(@RequestBody WhitelistEntity entity, HttpServletRequest request) {
        try {
            WhitelistEntity saved = whitelistService.add(entity);
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "白名单管理",
                    "新增白名单：" + entity.getWhitelistType() + " - " + entity.getWhitelistValue(),
                    "add", "/api/whitelist/add", getClientIp(request), 0);
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("新增白名单失败：", e);
            return ApiResponse.error("新增失败");
        }
    }

    @PutMapping("/update")
    public ApiResponse<WhitelistEntity> update(@RequestBody WhitelistEntity entity, HttpServletRequest request) {
        try {
            WhitelistEntity saved = whitelistService.update(entity);
            if (saved == null) {
                return ApiResponse.notFound("白名单不存在");
            }
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "白名单管理",
                    "更新白名单ID：" + entity.getId(), "update", "/api/whitelist/update", getClientIp(request), 0);
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("更新白名单失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            boolean success = whitelistService.delete(id);
            if (!success) {
                return ApiResponse.notFound("白名单不存在");
            }
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "白名单管理",
                    "删除白名单ID：" + id, "delete", "/api/whitelist/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除白名单失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, HttpServletRequest request) {
        try {
            WhitelistEntity entity = whitelistService.getById(id);
            if (entity == null) {
                return ApiResponse.notFound("白名单不存在");
            }
            int newEnabled = entity.getEnabled() == 1 ? 0 : 1;
            whitelistService.toggleEnabled(id, newEnabled);
            String action = newEnabled == 1 ? "启用" : "禁用";
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "白名单管理",
                    action + "白名单ID：" + id, "toggle", "/api/whitelist/" + id + "/toggle", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("切换白名单状态失败：", e);
            return ApiResponse.error("操作失败");
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(HttpServletRequest request) {
        try {
            whitelistService.refreshWhitelistCache();
            operLogService.logOperation(authService.getCurrentUsername(), "REFRESH", "白名单管理",
                    "刷新白名单缓存", "refresh", "/api/whitelist/refresh", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("刷新白名单缓存失败：", e);
            return ApiResponse.error("刷新失败");
        }
    }

    @PutMapping("/batch-enable")
    public ApiResponse<Void> batchEnable(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要启用的白名单");
            }
            for (Long id : ids) {
                whitelistService.toggleEnabled(id, 1);
            }
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "白名单管理",
                    "批量启用白名单，共" + ids.size() + "条", "batchEnable", "/api/whitelist/batch-enable", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量启用白名单失败：", e);
            return ApiResponse.error("批量启用失败");
        }
    }

    @PutMapping("/batch-disable")
    public ApiResponse<Void> batchDisable(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要禁用的白名单");
            }
            for (Long id : ids) {
                whitelistService.toggleEnabled(id, 0);
            }
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "白名单管理",
                    "批量禁用白名单，共" + ids.size() + "条", "batchDisable", "/api/whitelist/batch-disable", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量禁用白名单失败：", e);
            return ApiResponse.error("批量禁用失败");
        }
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDelete(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要删除的白名单");
            }
            for (Long id : ids) {
                whitelistService.delete(id);
            }
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "白名单管理",
                    "批量删除白名单，共" + ids.size() + "条", "batchDelete", "/api/whitelist/batch", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量删除白名单失败：", e);
            return ApiResponse.error("批量删除失败");
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getWhitelistStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            long totalWhitelists = whitelistService.countByCondition(null, null, null);
            long enabledWhitelists = whitelistService.countByCondition(null, null, 1);
            long disabledWhitelists = whitelistService.countByCondition(null, null, 0);
            
            long pathWhitelists = whitelistService.countByCondition("PATH", null, null);
            long headerWhitelists = whitelistService.countByCondition("HEADER", null, null);
            long ipWhitelists = whitelistService.countByCondition("IP", null, null);
            
            stats.put("totalWhitelists", totalWhitelists);
            stats.put("enabledWhitelists", enabledWhitelists);
            stats.put("disabledWhitelists", disabledWhitelists);
            stats.put("pathWhitelists", pathWhitelists);
            stats.put("headerWhitelists", headerWhitelists);
            stats.put("ipWhitelists", ipWhitelists);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取白名单统计失败：", e);
            return ApiResponse.error("获取统计失败");
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
