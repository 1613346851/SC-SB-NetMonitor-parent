package com.network.monitor.controller.outer;

import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.BlacklistAddDTO;
import com.network.monitor.dto.BlacklistInfoDTO;
import com.network.monitor.service.BlacklistManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑名单管理控制器（对外前端业务接口）
 * 支持前端页面手动添加/解除黑名单，自定义过期时间
 */
@Slf4j
@RestController
@RequestMapping("/api/blacklist")
public class BlacklistManageController {

    @Autowired
    private BlacklistManageService blacklistManageService;

    @Autowired
    private SysConfigCache sysConfigCache;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取黑名单列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getBlacklist(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            List<BlacklistInfoDTO> list = blacklistManageService.getBlacklistGroupedByIp();
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", list.size());
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取黑名单列表失败：", e);
            return ApiResponse.error("获取黑名单列表失败");
        }
    }

    /**
     * 获取黑名单详情（包含历史记录）
     */
    @GetMapping("/{ip}/history")
    public ApiResponse<Map<String, Object>> getBlacklistHistory(@PathVariable String ip) {
        try {
            Map<String, Object> info = blacklistManageService.getBlacklistWithHistory(ip);
            if (info.isEmpty()) {
                return ApiResponse.notFound("IP 不在黑名单中");
            }
            return ApiResponse.success(info);
        } catch (Exception e) {
            log.error("获取黑名单详情失败：ip={}", ip, e);
            return ApiResponse.error("获取黑名单详情失败");
        }
    }

    /**
     * 延长黑名单封禁时间
     */
    @PutMapping("/{ip}/extend")
    public ApiResponse<Void> extendBlacklist(@PathVariable String ip, @RequestBody Map<String, Object> params) {
        try {
            Integer expireSeconds = (Integer) params.get("expireSeconds");
            if (expireSeconds == null || expireSeconds <= 0) {
                return ApiResponse.error("延长时间必须大于0");
            }
            
            blacklistManageService.extendBlacklistExpireTime(ip, expireSeconds.longValue());
            
            log.info("延长黑名单封禁时间成功：ip={}, extendSeconds={}", ip, expireSeconds);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("延长黑名单封禁时间失败：ip={}", ip, e);
            return ApiResponse.error("延长封禁时间失败：" + e.getMessage());
        }
    }

    /**
     * 删除 IP 的所有黑名单记录
     */
    @DeleteMapping("/{ip}/all")
    public ApiResponse<Map<String, Object>> deleteAllBlacklists(@PathVariable String ip) {
        try {
            int count = blacklistManageService.deleteAllBlacklistsByIp(ip);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", count);
            
            log.info("删除 IP 的所有黑名单记录成功：ip={}, count={}", ip, count);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除 IP 的所有黑名单记录失败：ip={}", ip, e);
            return ApiResponse.error("删除黑名单记录失败：" + e.getMessage());
        }
    }

    /**
     * 添加 IP 到黑名单
     */
    @PostMapping
    public ApiResponse<Void> addToBlacklist(@RequestBody BlacklistAddDTO dto) {
        try {
            String ip = dto.getIpAddress();
            String reason = dto.getReason();
            Integer expireSeconds = dto.getExpireSeconds();
            String operator = dto.getOperator();

            // 计算过期时间
            LocalDateTime expireTime;
            if (expireSeconds != null && expireSeconds > 0) {
                expireTime = LocalDateTime.now().plusSeconds(expireSeconds);
            } else {
                // 从配置缓存获取默认过期时间（秒）
                int defaultExpireSeconds = sysConfigCache.getIntValue("blacklist.default.expire.seconds", 86400);
                expireTime = LocalDateTime.now().plusSeconds(defaultExpireSeconds);
            }

            // 操作人默认为"admin"
            if (operator == null || operator.isEmpty()) {
                operator = "admin";
            }

            blacklistManageService.addToBlacklist(ip, reason, expireTime, operator);

            log.info("手动添加黑名单成功：ip={}, reason={}, expireTime={}, operator={}", 
                    ip, reason, expireTime.format(TIME_FORMATTER), operator);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("添加 IP 到黑名单失败：", e);
            return ApiResponse.error("添加黑名单失败：" + e.getMessage());
        }
    }

    /**
     * 从黑名单移除 IP
     */
    @DeleteMapping("/{ip}")
    public ApiResponse<Void> removeFromBlacklist(@PathVariable String ip) {
        try {
            blacklistManageService.removeFromBlacklist(ip);

            log.info("从黑名单移除 IP 成功：ip={}", ip);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("从黑名单移除 IP 失败：ip={}", ip, e);
            return ApiResponse.error("移除黑名单失败：" + e.getMessage());
        }
    }

    /**
     * 检查 IP 是否在黑名单中
     */
    @GetMapping("/check/{ip}")
    public ApiResponse<Map<String, Object>> checkIp(@PathVariable String ip) {
        try {
            Map<String, Object> result = new HashMap<>();
            boolean inBlacklist = blacklistManageService.isInBlacklist(ip);
            result.put("ip", ip);
            result.put("inBlacklist", inBlacklist);

            if (inBlacklist) {
                Map<String, Object> info = blacklistManageService.getBlacklistInfo(ip);
                result.putAll(info);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("检查 IP 失败：ip={}", ip, e);
            return ApiResponse.error("检查 IP 失败");
        }
    }

    /**
     * 获取黑名单详情
     */
    @GetMapping("/{ip}")
    public ApiResponse<Map<String, Object>> getBlacklistDetail(@PathVariable String ip) {
        try {
            Map<String, Object> info = blacklistManageService.getBlacklistInfo(ip);
            if (info.isEmpty()) {
                return ApiResponse.notFound("IP 不在黑名单中");
            }
            return ApiResponse.success(info);
        } catch (Exception e) {
            log.error("获取黑名单详情失败：ip={}", ip, e);
            return ApiResponse.error("获取黑名单详情失败");
        }
    }

    /**
     * 清理过期黑名单
     */
    @PostMapping("/clean-expired")
    public ApiResponse<Map<String, Object>> cleanExpired() {
        try {
            int count = blacklistManageService.cleanExpiredBlacklist();

            Map<String, Object> result = new HashMap<>();
            result.put("cleanedCount", count);

            log.info("清理过期黑名单完成，数量：{}", count);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("清理过期黑名单失败：", e);
            return ApiResponse.error("清理过期黑名单失败");
        }
    }

    /**
     * 清空所有黑名单
     */
    @PostMapping("/clear")
    public ApiResponse<Void> clear() {
        try {
            blacklistManageService.clear();

            log.info("清空所有黑名单完成");

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("清空所有黑名单失败：", e);
            return ApiResponse.error("清空黑名单失败：" + e.getMessage());
        }
    }

    /**
     * 获取黑名单统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", blacklistManageService.getSize());

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取黑名单统计信息失败：", e);
            return ApiResponse.error("获取统计信息失败");
        }
    }
}
