package com.network.monitor.controller.outer;

import com.network.monitor.cache.BlacklistCache;
import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.util.IpNormalizeUtil;
import com.network.monitor.dto.BlacklistAddDTO;
import com.network.monitor.dto.BlacklistInfoDTO;
import com.network.monitor.entity.IpBlacklistEntity;
import com.network.monitor.entity.IpBlacklistHistoryEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.IpBlacklistMapper;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.BlacklistManageService;
import com.network.monitor.service.IpBlacklistService;
import com.network.monitor.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/blacklist")
public class BlacklistManageController {

    @Autowired
    private BlacklistManageService blacklistManageService;

    @Autowired
    private IpBlacklistService ipBlacklistService;

    @Autowired
    private SysConfigCache sysConfigCache;

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private IpBlacklistMapper ipBlacklistMapper;

    @Autowired
    private BlacklistCache blacklistCache;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getBlacklist(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            List<IpBlacklistEntity> entities = ipBlacklistService.getAllBlacklists();
            
            List<BlacklistInfoDTO> list = new ArrayList<>();
            for (IpBlacklistEntity entity : entities) {
                BlacklistInfoDTO dto = new BlacklistInfoDTO();
                dto.setId(entity.getId());
                dto.setIp(entity.getIpAddress());
                dto.setReason(getLatestBanReason(entity.getId()));
                dto.setExpireTime(entity.getCurrentExpireTime() != null ? 
                    entity.getCurrentExpireTime().format(TIME_FORMATTER) : null);
                dto.setCreateTime(entity.getCreateTime() != null ? 
                    entity.getCreateTime().format(TIME_FORMATTER) : null);
                dto.setStatus(entity.isBanned() && !entity.isExpired() ? 1 : 2);
                dto.setRemainingSeconds(calculateRemainingSeconds(entity.getCurrentExpireTime()));
                dto.setRemainingTime(formatRemainingTime(entity.getCurrentExpireTime()));
                dto.setTotalBanCount(entity.getTotalBanCount() != null ? entity.getTotalBanCount() : 0);
                list.add(dto);
            }
            
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

    private String getLatestBanReason(Long blacklistId) {
        try {
            List<IpBlacklistHistoryEntity> historyList = ipBlacklistService.getHistoryByIp(
                ipBlacklistService.getBlacklistByIp(null) != null ? 
                    ipBlacklistService.getBlacklistByIp(null).getIpAddress() : null);
            if (historyList != null && !historyList.isEmpty()) {
                return historyList.get(0).getBanReason();
            }
        } catch (Exception e) {
            log.warn("获取封禁原因失败", e);
        }
        return "手动添加";
    }

    @GetMapping("/{ip}/history")
    public ApiResponse<Map<String, Object>> getBlacklistHistory(@PathVariable String ip) {
        try {
            IpBlacklistEntity entity = ipBlacklistService.getBlacklistByIp(ip);
            if (entity == null) {
                return ApiResponse.notFound("IP 不在黑名单中");
            }

            List<IpBlacklistHistoryEntity> historyList = ipBlacklistService.getHistoryByIp(ip);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", entity.getId());
            result.put("ip", entity.getIpAddress());
            result.put("expireTime", entity.getCurrentExpireTime() != null ? 
                entity.getCurrentExpireTime().format(TIME_FORMATTER) : null);
            result.put("createTime", entity.getCreateTime() != null ? 
                entity.getCreateTime().format(TIME_FORMATTER) : null);
            result.put("status", entity.isBanned() && !entity.isExpired() ? 1 : 2);
            result.put("totalBanCount", entity.getTotalBanCount());

            List<Map<String, Object>> historyResult = new ArrayList<>();
            if (historyList != null) {
                for (IpBlacklistHistoryEntity history : historyList) {
                    Map<String, Object> historyMap = new HashMap<>();
                    historyMap.put("id", history.getId());
                    historyMap.put("reason", history.getBanReason());
                    historyMap.put("expireTime", history.getExpireTime() != null ? 
                        history.getExpireTime().format(TIME_FORMATTER) : null);
                    historyMap.put("createTime", history.getBanExecuteTime() != null ? 
                        history.getBanExecuteTime().format(TIME_FORMATTER) : null);
                    historyMap.put("operator", history.getOperator());
                    historyMap.put("status", history.isBanning() && !history.isExpired() ? 1 : 2);
                    historyMap.put("banDuration", history.getBanDuration());
                    historyMap.put("banDurationText", formatBanDuration(history.getBanDuration()));
                    historyResult.add(historyMap);
                }
            }
            result.put("history", historyResult);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取黑名单详情失败：ip={}", ip, e);
            return ApiResponse.error("获取黑名单详情失败");
        }
    }

    @PutMapping("/{ip}/extend")
    public ApiResponse<Void> extendBlacklist(@PathVariable String ip, @RequestBody Map<String, Object> params, HttpServletRequest request) {
        try {
            Integer expireSeconds = (Integer) params.get("expireSeconds");
            if (expireSeconds == null || expireSeconds <= 0) {
                return ApiResponse.error("延长时间必须大于0");
            }
            
            ipBlacklistService.extendBlacklist(ip, expireSeconds.longValue(), authService.getCurrentUsername());
            
            log.info("延长黑名单封禁时间成功：ip={}, extendSeconds={}", ip, expireSeconds);
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "黑名单管理", 
                "延长黑名单时间：IP=" + ip + "，延长时间=" + formatBanDuration(expireSeconds.longValue()), "extend", "/api/blacklist/" + ip + "/extend", getClientIp(request), 0);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("延长黑名单封禁时间失败：ip={}", ip, e);
            return ApiResponse.error("延长封禁时间失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{ip}/all")
    public ApiResponse<Map<String, Object>> deleteAllBlacklists(@PathVariable String ip, HttpServletRequest request) {
        try {
            String normalizedIp = IpNormalizeUtil.normalize(ip);

            ipBlacklistMapper.deleteByIpAddress(normalizedIp);
            blacklistCache.remove(normalizedIp);
            defenseLogMapper.deleteAllBlacklistsByIp(normalizedIp);

            ipBlacklistService.syncToGateway(normalizedIp, "REMOVE");

            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", 1);

            log.info("删除 IP 的所有黑名单记录成功：ip={}", ip);

            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "黑名单管理",
                    "删除黑名单IP：" + ip, "delete", "/api/blacklist/" + ip + "/all", getClientIp(request), 0);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除 IP 的所有黑名单记录失败：ip={}", ip, e);
            return ApiResponse.error("删除黑名单记录失败：" + e.getMessage());
        }
    }

    @PostMapping("/{ip}/unblock")
    public ApiResponse<Void> unblockIp(@PathVariable String ip, @RequestBody Map<String, Object> params, HttpServletRequest request) {
        try {
            String unbanReason = params.get("reason") != null ? (String) params.get("reason") : "手动解禁";
            String operator = authService.getCurrentUsername();

            ipBlacklistService.removeFromBlacklist(ip, unbanReason, operator);

            log.info("解禁 IP 成功：ip={}, reason={}, operator={}", ip, unbanReason, operator);

            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "黑名单管理",
                    "解禁IP：" + ip + "，原因：" + unbanReason, "unblock", "/api/blacklist/" + ip + "/unblock", getClientIp(request), 0);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("解禁 IP 失败：ip={}", ip, e);
            return ApiResponse.error("解禁失败：" + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Void> addToBlacklist(@RequestBody BlacklistAddDTO dto, HttpServletRequest request) {
        try {
            String ip = dto.getIpAddress();
            String reason = dto.getReason();
            Integer expireSeconds = dto.getExpireSeconds();
            String operator = dto.getOperator();
            Boolean isPermanent = dto.getIsPermanent();

            LocalDateTime expireTime;
            if (Boolean.TRUE.equals(isPermanent)) {
                expireTime = null;
            } else if (expireSeconds != null && expireSeconds > 0) {
                expireTime = LocalDateTime.now().plusSeconds(expireSeconds);
            } else {
                int defaultExpireSeconds = sysConfigCache.getIntValue("blacklist.default.expire.seconds", 86400);
                expireTime = LocalDateTime.now().plusSeconds(defaultExpireSeconds);
            }

            if (operator == null || operator.isEmpty()) {
                operator = authService.getCurrentUsername();
            }

            ipBlacklistService.addToBlacklist(ip, reason, expireTime, operator, "MANUAL", null, null, null);

            log.info("手动添加黑名单成功：ip={}, reason={}, expireTime={}, operator={}", 
                    ip, reason, expireTime != null ? expireTime.format(TIME_FORMATTER) : "永久", operator);

            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "黑名单管理", 
                "添加黑名单IP：" + ip, "add", "/api/blacklist", getClientIp(request), 0);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("添加 IP 到黑名单失败：", e);
            return ApiResponse.error("添加黑名单失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{ip}")
    public ApiResponse<Void> removeFromBlacklist(@PathVariable String ip, HttpServletRequest request) {
        try {
            ipBlacklistService.removeFromBlacklist(ip, "手动移除黑名单", authService.getCurrentUsername());

            log.info("从黑名单移除 IP 成功：ip={}", ip);

            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "黑名单管理", 
                "移除黑名单IP：" + ip, "remove", "/api/blacklist/" + ip, getClientIp(request), 0);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("从黑名单移除 IP 失败：ip={}", ip, e);
            return ApiResponse.error("移除黑名单失败：" + e.getMessage());
        }
    }

    @GetMapping("/check/{ip}")
    public ApiResponse<Map<String, Object>> checkIp(@PathVariable String ip) {
        try {
            Map<String, Object> result = new HashMap<>();
            boolean inBlacklist = ipBlacklistService.isInBlacklist(ip);
            result.put("ip", ip);
            result.put("inBlacklist", inBlacklist);

            if (inBlacklist) {
                IpBlacklistEntity entity = ipBlacklistService.getBlacklistByIp(ip);
                if (entity != null) {
                    result.put("expireTime", entity.getCurrentExpireTime() != null ? 
                        entity.getCurrentExpireTime().format(TIME_FORMATTER) : null);
                    result.put("createTime", entity.getCreateTime() != null ? 
                        entity.getCreateTime().format(TIME_FORMATTER) : null);
                    result.put("status", entity.isBanned() && !entity.isExpired() ? 1 : 2);
                }
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("检查 IP 失败：ip={}", ip, e);
            return ApiResponse.error("检查 IP 失败");
        }
    }

    @GetMapping("/{ip}")
    public ApiResponse<Map<String, Object>> getBlacklistDetail(@PathVariable String ip) {
        try {
            IpBlacklistEntity entity = ipBlacklistService.getBlacklistByIp(ip);
            if (entity == null) {
                return ApiResponse.notFound("IP 不在黑名单中");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", entity.getId());
            result.put("ip", entity.getIpAddress());
            result.put("expireTime", entity.getCurrentExpireTime() != null ? 
                entity.getCurrentExpireTime().format(TIME_FORMATTER) : null);
            result.put("createTime", entity.getCreateTime() != null ? 
                entity.getCreateTime().format(TIME_FORMATTER) : null);
            result.put("status", entity.isBanned() && !entity.isExpired() ? 1 : 2);
            result.put("totalBanCount", entity.getTotalBanCount());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取黑名单详情失败：ip={}", ip, e);
            return ApiResponse.error("获取黑名单详情失败");
        }
    }

    @PostMapping("/clean-expired")
    public ApiResponse<Map<String, Object>> cleanExpired() {
        try {
            int count = ipBlacklistService.cleanExpiredBlacklists();

            Map<String, Object> result = new HashMap<>();
            result.put("cleanedCount", count);

            log.info("清理过期黑名单完成，数量：{}", count);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("清理过期黑名单失败：", e);
            return ApiResponse.error("清理过期黑名单失败");
        }
    }

    @PostMapping("/clear")
    public ApiResponse<Void> clear() {
        try {
            ipBlacklistService.clear();

            log.info("清空所有黑名单完成");

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("清空所有黑名单失败：", e);
            return ApiResponse.error("清空黑名单失败：" + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", ipBlacklistService.getTotalCount());
            stats.put("banningCount", ipBlacklistService.getBanningCount());

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取黑名单统计信息失败：", e);
            return ApiResponse.error("获取统计信息失败");
        }
    }

    private Long calculateRemainingSeconds(LocalDateTime expireTime) {
        if (expireTime == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expireTime)) {
            return 0L;
        }
        return java.time.Duration.between(now, expireTime).getSeconds();
    }

    private String formatRemainingTime(LocalDateTime expireTime) {
        Long remainingSeconds = calculateRemainingSeconds(expireTime);
        if (remainingSeconds == null) {
            return "永久";
        }
        if (remainingSeconds <= 0) {
            return "已过期";
        }
        
        long days = remainingSeconds / (24 * 3600);
        long hours = (remainingSeconds % (24 * 3600)) / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        long seconds = remainingSeconds % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("秒");
        }
        
        return sb.toString();
    }

    private String formatBanDuration(Long banDuration) {
        if (banDuration == null) {
            return "永久";
        }
        if (banDuration <= 0) {
            return "-";
        }
        
        long days = banDuration / (24 * 3600);
        long hours = (banDuration % (24 * 3600)) / 3600;
        long minutes = (banDuration % 3600) / 60;
        long seconds = banDuration % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("秒");
        }
        
        return sb.toString();
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
