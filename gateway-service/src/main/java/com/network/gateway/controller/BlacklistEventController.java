package com.network.gateway.controller;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.IpBlacklistCache;
import com.network.gateway.defense.DefenseLogType;
import com.network.gateway.dto.BlacklistEventDTO;
import com.network.gateway.dto.DefenseLogDTO;
import com.network.gateway.service.BlacklistDegradationService;
import com.network.gateway.util.DefenseLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/inner/blacklist")
public class BlacklistEventController {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistEventController.class);

    @Value("${cross-service.security.secret-key:NetMonitor2026SecretKey}")
    private String secretKey;

    @Autowired
    private IpBlacklistCache blacklistCache;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private BlacklistDegradationService degradationService;

    @PostMapping("/event")
    public ResponseEntity<Map<String, Object>> receiveBlacklistEvent(
            @RequestBody BlacklistEventDTO event,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!validateRequest(requestId, timestamp, signature)) {
                logger.warn("黑名单事件请求验证失败: requestId={}", requestId);
                response.put("success", false);
                response.put("message", "请求验证失败");
                response.put("errorCode", "AUTH_FAILED");
                return ResponseEntity.status(401).body(response);
            }

            if (event == null || !StringUtils.hasText(event.getIp())) {
                response.put("success", false);
                response.put("message", "无效的黑名单事件");
                response.put("errorCode", "INVALID_EVENT");
                return ResponseEntity.badRequest().body(response);
            }

            boolean success = processBlacklistEvent(event);
            
            if (success) {
                response.put("success", true);
                response.put("message", "黑名单事件处理成功");
                response.put("ip", event.getIp());
                response.put("traceId", event.getTraceId());
                logger.info("黑名单事件处理成功: ip={}, banType={}, traceId={}", 
                    event.getIp(), event.getBanType(), event.getTraceId());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "黑名单事件处理失败");
                response.put("errorCode", "PROCESSING_FAILED");
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("处理黑名单事件异常: ip={}", event != null ? event.getIp() : "null", e);
            
            if (event != null) {
                degradationService.saveToDegradationCache(event);
            }
            
            response.put("success", false);
            response.put("message", "处理异常: " + e.getMessage());
            response.put("errorCode", "SYSTEM_ERROR");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/status/{ip}")
    public ResponseEntity<Map<String, Object>> getBlacklistStatus(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!StringUtils.hasText(ip)) {
                response.put("success", false);
                response.put("message", "IP地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            boolean inBlacklist = blacklistCache.isInBlacklist(ip);
            Long expireTime = blacklistCache.getBlacklistExpireTime(ip);
            long remainingTime = blacklistCache.getRemainingTime(ip);
            
            response.put("success", true);
            response.put("ip", ip);
            response.put("inBlacklist", inBlacklist);
            response.put("expireTime", expireTime);
            response.put("remainingTimeMs", remainingTime);
            
            if (inBlacklist && expireTime != null) {
                response.put("isPermanent", false);
                response.put("remainingSeconds", remainingTime / 1000);
            } else if (inBlacklist) {
                response.put("isPermanent", true);
            }
            
            Integer state = attackStateCache.getState(ip);
            response.put("attackState", state);
            response.put("attackStateName", getStateName(state));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询黑名单状态异常: ip={}", ip, e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/unban/{ip}")
    public ResponseEntity<Map<String, Object>> manualUnban(
            @PathVariable String ip,
            @RequestParam(value = "operator", defaultValue = "MANUAL") String operator,
            @RequestParam(value = "reason", defaultValue = "人工解封") String reason) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!StringUtils.hasText(ip)) {
                response.put("success", false);
                response.put("message", "IP地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            boolean inBlacklist = blacklistCache.isInBlacklist(ip);
            
            if (!inBlacklist) {
                response.put("success", false);
                response.put("message", "IP不在黑名单中");
                response.put("ip", ip);
                return ResponseEntity.ok(response);
            }

            boolean removed = blacklistCache.removeFromBlacklist(ip);
            
            if (removed) {
                attackStateCache.markAsCooldown(ip);
                
                DefenseLogDTO log = DefenseLogUtil.buildManualUnbanLog(ip, 
                    "UNBAN_" + System.currentTimeMillis(), operator, reason);
                degradationService.recordUnbanLog(log);
                
                response.put("success", true);
                response.put("message", "IP已从黑名单移除");
                response.put("ip", ip);
                response.put("operator", operator);
                logger.info("人工解封成功: ip={}, operator={}, reason={}", ip, operator, reason);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "解封操作失败");
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("人工解封异常: ip={}", ip, e);
            response.put("success", false);
            response.put("message", "解封失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/batch/unban")
    public ResponseEntity<Map<String, Object>> batchUnban(
            @RequestBody Map<String, Object> request,
            @RequestParam(value = "operator", defaultValue = "MANUAL") String operator) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<String> ips = (List<String>) request.get("ips");
            String reason = (String) request.getOrDefault("reason", "批量人工解封");
            
            if (ips == null || ips.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP列表不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            int successCount = 0;
            int failCount = 0;
            
            for (String ip : ips) {
                try {
                    boolean inBlacklist = blacklistCache.isInBlacklist(ip);
                    if (inBlacklist) {
                        boolean removed = blacklistCache.removeFromBlacklist(ip);
                        if (removed) {
                            attackStateCache.markAsCooldown(ip);
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    logger.warn("批量解封单个IP失败: ip={}", ip, e);
                }
            }

            response.put("success", true);
            response.put("message", String.format("批量解封完成: 成功%d, 失败%d", successCount, failCount));
            response.put("successCount", successCount);
            response.put("failCount", failCount);
            response.put("operator", operator);
            
            logger.info("批量解封完成: 成功={}, 失败={}, operator={}", successCount, failCount, operator);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("批量解封异常", e);
            response.put("success", false);
            response.put("message", "批量解封失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listBlacklist(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            java.util.Set<String> allIps = blacklistCache.getAllBlacklistedIps();
            
            int total = allIps.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            
            List<String> ipList = allIps.stream()
                .skip(start)
                .limit(size)
                .collect(java.util.stream.Collectors.toList());
            
            response.put("success", true);
            response.put("total", total);
            response.put("page", page);
            response.put("size", size);
            response.put("data", ipList);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询黑名单列表异常", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/expiring")
    public ResponseEntity<Map<String, Object>> getExpiringSoon(
            @RequestParam(value = "thresholdMinutes", defaultValue = "10") int thresholdMinutes) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            long thresholdMs = thresholdMinutes * 60 * 1000L;
            java.util.Set<String> expiringIps = blacklistCache.getExpiringSoon(thresholdMs);
            
            response.put("success", true);
            response.put("thresholdMinutes", thresholdMinutes);
            response.put("count", expiringIps.size());
            response.put("ips", expiringIps);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询即将过期黑名单异常", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("blacklistSize", blacklistCache.getSize());
            response.put("cacheStats", blacklistCache.getStats());
            response.put("degradationStats", degradationService.getStats());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取黑名单统计异常", e);
            response.put("success", false);
            response.put("message", "获取统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/extend/{ip}")
    public ResponseEntity<Map<String, Object>> extendBlacklist(
            @PathVariable String ip,
            @RequestParam(value = "additionalMinutes", defaultValue = "10") int additionalMinutes,
            @RequestParam(value = "operator", defaultValue = "MANUAL") String operator) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!StringUtils.hasText(ip)) {
                response.put("success", false);
                response.put("message", "IP地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            boolean inBlacklist = blacklistCache.isInBlacklist(ip);
            
            if (!inBlacklist) {
                response.put("success", false);
                response.put("message", "IP不在黑名单中，无法延长");
                return ResponseEntity.badRequest().body(response);
            }

            long additionalMs = additionalMinutes * 60 * 1000L;
            boolean extended = blacklistCache.extendBlacklistTime(ip, additionalMs);
            
            if (extended) {
                Long newExpireTime = blacklistCache.getBlacklistExpireTime(ip);
                response.put("success", true);
                response.put("message", "黑名单时间已延长");
                response.put("ip", ip);
                response.put("additionalMinutes", additionalMinutes);
                response.put("newExpireTime", newExpireTime);
                response.put("operator", operator);
                
                logger.info("延长黑名单时间: ip={}, additionalMinutes={}, operator={}", 
                    ip, additionalMinutes, operator);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "延长操作失败");
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("延长黑名单时间异常: ip={}", ip, e);
            response.put("success", false);
            response.put("message", "延长失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private boolean validateRequest(String requestId, String timestamp, String signature) {
        if (!StringUtils.hasText(requestId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return true;
        }
        
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > 300000) {
                logger.warn("请求时间戳过期: timestamp={}, now={}", ts, now);
                return false;
            }
            
            return true;
        } catch (NumberFormatException e) {
            logger.warn("时间戳格式错误: timestamp={}", timestamp);
            return false;
        }
    }

    private boolean processBlacklistEvent(BlacklistEventDTO event) {
        String ip = event.getIp();
        Long expireTimestamp = event.getExpireTimestamp();
        
        if (expireTimestamp == null || expireTimestamp <= 0) {
            expireTimestamp = System.currentTimeMillis() + 3600000;
        }
        
        boolean added = blacklistCache.addToBlacklist(ip, expireTimestamp);
        
        if (added) {
            if (event.getToState() > 0) {
                attackStateCache.updateState(ip, event.getToState(), event.getEventId());
            } else {
                attackStateCache.markAsDefended(ip, event.getEventId());
            }
            
            logger.info("黑名单事件处理: ip={}, expireTime={}, eventId={}", 
                ip, expireTimestamp, event.getEventId());
        }
        
        return added;
    }

    private String getStateName(Integer state) {
        if (state == null) {
            return "未知";
        }
        switch (state) {
            case 0: return "正常";
            case 1: return "可疑";
            case 2: return "攻击中";
            case 3: return "已防御";
            case 4: return "冷却期";
            default: return "未知";
        }
    }
}
