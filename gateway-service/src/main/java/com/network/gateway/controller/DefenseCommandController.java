package com.network.gateway.controller;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.IpBlacklistCache;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.dto.DefenseCommandDTO;
import com.network.gateway.exception.GatewayBizException;
import com.network.gateway.filter.defense.IpBlacklistDefenseFilter;
import com.network.gateway.filter.defense.MaliciousRequestBlockFilter;
import com.network.gateway.filter.defense.RequestRateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/gateway/defense")
public class DefenseCommandController {

    private static final Logger logger = LoggerFactory.getLogger(DefenseCommandController.class);

    @Value("${gateway.defense.auth.token:NetMonitor2026SecureToken}")
    private String configuredAuthToken;

    @Value("${gateway.defense.auth.allowed-ips:127.0.0.1,localhost}")
    private String allowedMonitorServiceIps;

    @Autowired
    private IpBlacklistDefenseFilter blacklistFilter;

    @Autowired
    private RequestRateLimitFilter rateLimitFilter;

    @Autowired
    private MaliciousRequestBlockFilter maliciousRequestFilter;

    @Autowired
    private IpBlacklistCache blacklistCache;

    @Autowired
    private RequestRateLimitCache rateLimitCache;

    @Autowired
    private IpAttackStateCache attackStateCache;

    /**
     * 接收防御指令
     * 支持 Token 鉴权和请求来源 IP 验证
     *
     * @param commandDTO 防御指令 DTO
     * @param authToken 授权 Token（从请求头获取）
     * @param sourceIp 请求来源 IP（从请求头获取）
     * @return 响应结果
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> receiveDefenseCommand(
            @RequestBody DefenseCommandDTO commandDTO,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestHeader(value = "X-Source-IP", required = false) String sourceIp) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 第一步：Token 鉴权
            if (!validateAuthToken(authToken)) {
                logger.warn("防御指令鉴权失败：Token 无效");
                response.put("success", false);
                response.put("message", "授权 Token 无效");
                response.put("errorCode", "AUTH_TOKEN_INVALID");
                return ResponseEntity.status(401).body(response);
            }

            // 第二步：验证请求来源 IP
            if (!validateSourceIp(sourceIp)) {
                logger.warn("防御指令来源 IP 验证失败：IP={}", sourceIp);
                response.put("success", false);
                response.put("message", "请求来源 IP 不被允许");
                response.put("errorCode", "SOURCE_IP_FORBIDDEN");
                return ResponseEntity.status(403).body(response);
            }

            // 第三步：验证指令有效性
            if (commandDTO == null || !commandDTO.isValid()) {
                response.put("success", false);
                response.put("message", "无效的防御指令");
                response.put("errorCode", "INVALID_COMMAND");
                logger.warn("接收到无效的防御指令：{}", commandDTO);
                return ResponseEntity.badRequest().body(response);
            }

            // 第四步：验证指令是否已过期
            if (commandDTO.isExpired()) {
                response.put("success", false);
                response.put("message", "防御指令已过期");
                response.put("errorCode", "COMMAND_EXPIRED");
                logger.warn("防御指令已过期：指令 ID={}", commandDTO.getCommandId());
                return ResponseEntity.badRequest().body(response);
            }

            // 第五步：根据指令类型执行相应的防御操作
            boolean success = executeDefenseCommand(commandDTO);
            
            if (success) {
                response.put("success", true);
                response.put("message", "防御指令执行成功");
                response.put("commandId", commandDTO.getCommandId());
                response.put("remainingTime", commandDTO.getRemainingTime());
                logger.info("防御指令执行成功：指令 ID[{}] 类型 [{}] IP[{}] 来源 IP[{}]", 
                           commandDTO.getCommandId(), 
                           commandDTO.getDefenseType(), 
                           commandDTO.getSourceIp(),
                           sourceIp);
            } else {
                response.put("success", false);
                response.put("message", "防御指令执行失败");
                response.put("commandId", commandDTO.getCommandId());
                response.put("errorCode", "EXECUTION_FAILED");
                logger.error("防御指令执行失败：指令 ID[{}] 类型 [{}] IP[{}]", 
                            commandDTO.getCommandId(), 
                            commandDTO.getDefenseType(), 
                            commandDTO.getSourceIp());
            }

            return ResponseEntity.ok(response);

        } catch (GatewayBizException e) {
            logger.error("处理防御指令时发生业务异常：指令 ID[{}]", 
                        commandDTO != null ? commandDTO.getCommandId() : "unknown", e);
            
            response.put("success", false);
            response.put("message", "业务异常：" + e.getMessage());
            response.put("errorCode", e.getErrorCode());
            return ResponseEntity.status(400).body(response);
            
        } catch (Exception e) {
            logger.error("处理防御指令时发生系统异常：指令 ID[{}]", 
                        commandDTO != null ? commandDTO.getCommandId() : "unknown", e);
            
            response.put("success", false);
            response.put("message", "处理防御指令时发生系统异常：" + e.getMessage());
            response.put("errorCode", "SYSTEM_ERROR");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 验证授权 Token
     *
     * @param authToken 请求中的 Token
     * @return true 表示验证通过
     */
    private boolean validateAuthToken(String authToken) {
        if (!StringUtils.hasText(authToken)) {
            logger.debug("Token 鉴权失败：请求中未提供 Token");
            return false;
        }
        
        boolean isValid = Objects.equals(configuredAuthToken, authToken);
        if (!isValid) {
            logger.debug("Token 鉴权失败：Token 不匹配");
        }
        return isValid;
    }

    /**
     * 验证请求来源 IP
     *
     * @param sourceIp 请求来源 IP
     * @return true 表示验证通过
     */
    private boolean validateSourceIp(String sourceIp) {
        if (!StringUtils.hasText(sourceIp)) {
            // 如果没有提供来源 IP，允许（兼容旧版本）
            logger.debug("未提供来源 IP，允许请求");
            return true;
        }
        
        // 检查 IP 是否在允许列表中
        String[] allowedIps = allowedMonitorServiceIps.split(",");
        for (String allowedIp : allowedIps) {
            if (allowedIp.trim().equals(sourceIp)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean executeDefenseCommand(DefenseCommandDTO commandDTO) {
        DefenseCommandDTO.DefenseType defenseType = commandDTO.getDefenseType();
        String sourceIp = commandDTO.getSourceIp();
        Long expireTime = commandDTO.getExpireTimestamp();
        String eventId = commandDTO.getEventId();
        String action = commandDTO.getAction();
        
        logger.info("执行防御指令: defenseType={}, sourceIp={}, action={}, expireTime={}", 
            defenseType, sourceIp, action, expireTime);

        boolean success = switch (defenseType) {
            case BLACKLIST -> {
                if ("REMOVE".equals(action)) {
                    logger.info("执行黑名单移除操作: IP={}", sourceIp);
                    boolean removed = blacklistFilter.removeFromBlacklist(sourceIp);
                    if (removed) {
                        attackStateCache.markAsCooldown(sourceIp);
                        logger.info("IP[{}]已从黑名单移除，状态更新为COOLDOWN", sourceIp);
                    } else {
                        logger.warn("IP[{}]从黑名单移除失败，可能不在黑名单中", sourceIp);
                    }
                    yield removed;
                } else {
                    logger.info("执行黑名单添加操作: IP={}, expireTime={}", sourceIp, expireTime);
                    if (eventId != null) {
                        attackStateCache.markAsDefended(sourceIp, eventId);
                        logger.info("IP[{}]已预先标记为DEFENDED状态，eventId={}", sourceIp, eventId);
                    }
                    boolean added = blacklistFilter.addToBlacklist(sourceIp, expireTime);
                    if (added) {
                        logger.info("IP[{}]已成功加入黑名单", sourceIp);
                    }
                    yield added;
                }
            }
            case RATE_LIMIT -> {
                int threshold = commandDTO.getRateLimitThreshold() != null ?
                        commandDTO.getRateLimitThreshold() : 5;
                rateLimitCache.setCustomThreshold(sourceIp, threshold, expireTime);
                rateLimitCache.resetRequestCount(sourceIp);
                logger.info("更新 IP[{}] 的限流阈值为{}次/秒，expireTime={}", sourceIp, threshold, expireTime);
                yield true;
            }

            case BLOCK -> {
                logger.info("请求拦截防御：IP[{}]，eventId={}，拦截已由攻击规则检测过滤器实时执行", sourceIp, eventId);
                yield true;
            }
        };

        return success;
    }

    /**
     * 获取网关防御状态
     *
     * @return 防御状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDefenseStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 收集各个过滤器的状态信息
            status.put("blacklistFilter", Map.of(
                    "enabled", true,
                    "blacklistSize", blacklistFilter.getBlacklistSize(),
                    "statistics", blacklistFilter.getStatistics()
            ));
            
            status.put("rateLimitFilter", Map.of(
                    "enabled", true,
                    "activeIpCount", rateLimitFilter.getActiveIpCount(),
                    "defaultThreshold", rateLimitFilter.getDefaultThreshold(),
                    "customThresholdCount", rateLimitCache.getCustomThresholdCount(),
                    "statistics", rateLimitFilter.getStatistics()
            ));

            
            status.put("maliciousRequestFilter", Map.of(
                    "enabled", true,
                    "maliciousIpCount", maliciousRequestFilter.getMaliciousIpCount(),
                    "statistics", maliciousRequestFilter.getStatistics()
            ));
            
            // 缓存状态
            status.put("cacheStatus", Map.of(
                    "blacklistCache", blacklistCache.getStats(),
                    "rateLimitCache", rateLimitCache.getStats()
            ));
            
            status.put("timestamp", System.currentTimeMillis());
            status.put("success", true);
            
            logger.debug("返回网关防御状态信息");
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("获取防御状态时发生异常", e);
            status.put("success", false);
            status.put("message", "获取防御状态失败：" + e.getMessage());
            return ResponseEntity.status(500).body(status);
        }
    }

    /**
     * 手动添加 IP 到黑名单
     *
     * @param request 请求参数
     * @return 响应结果
     */
    @PostMapping("/blacklist/add")
    public ResponseEntity<Map<String, Object>> addToBlacklist(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String ip = (String) request.get("ip");
            Integer expireMinutes = (Integer) request.get("expireMinutes");
            
            if (ip == null || ip.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP 地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            long expireTime = System.currentTimeMillis() + 
                            (expireMinutes != null ? expireMinutes * 60 * 1000L : 10 * 60 * 1000L); // 默认 10 分钟
            
            boolean added = blacklistFilter.addToBlacklist(ip, expireTime);
            
            if (added) {
                response.put("success", true);
                response.put("message", "IP 已成功加入黑名单");
                response.put("ip", ip);
                response.put("expireTime", expireTime);
                logger.info("手动添加 IP 到黑名单：{}", ip);
            } else {
                response.put("success", false);
                response.put("message", "添加 IP 到黑名单失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("手动添加黑名单时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/blacklist/remove/{ip}")
    public ResponseEntity<Map<String, Object>> removeFromBlacklist(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean removed = blacklistFilter.removeFromBlacklist(ip);
            
            if (removed) {
                attackStateCache.markAsCooldown(ip);
                logger.info("从黑名单移除 IP: {}，状态更新为COOLDOWN", ip);
            }
            
            if (removed) {
                response.put("success", true);
                response.put("message", "IP 已从黑名单中移除");
                response.put("ip", ip);
            } else {
                response.put("success", false);
                response.put("message", "IP 不在黑名单中或移除失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("从黑名单移除 IP 时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/state/sync")
    public ResponseEntity<Map<String, Object>> syncAttackState(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String ip = (String) request.get("ip");
            Integer state = (Integer) request.get("state");
            String eventId = (String) request.get("eventId");
            
            if (ip == null || ip.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP 地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (state != null) {
                if (eventId != null) {
                    attackStateCache.updateState(ip, state, eventId);
                } else {
                    attackStateCache.updateState(ip, state);
                }
                logger.info("同步攻击状态：ip={}, state={}, eventId={}", ip, state, eventId);
            }
            
            response.put("success", true);
            response.put("message", "攻击状态同步成功");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("同步攻击状态时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/state/reset/{ip}")
    public ResponseEntity<Map<String, Object>> resetAttackState(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            attackStateCache.resetToNormal(ip);
            logger.info("重置攻击状态：ip={}", ip);
            
            response.put("success", true);
            response.put("message", "攻击状态已重置");
            response.put("ip", ip);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("重置攻击状态时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 重置指定 IP 的请求计数
     *
     * @param ip IP 地址
     * @return 响应结果
     */
    @PostMapping("/ratelimit/reset/{ip}")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            rateLimitFilter.resetRequestCount(ip);
            response.put("success", true);
            response.put("message", "IP 请求计数已重置");
            response.put("ip", ip);
            logger.info("重置 IP 请求计数：{}", ip);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("重置请求计数时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Autowired
    private com.network.gateway.service.StateInterventionService interventionService;

    @PostMapping("/intervention/reset")
    public ResponseEntity<Map<String, Object>> forceResetToNormal(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String ip = (String) request.get("ip");
            String operator = (String) request.getOrDefault("operator", "MANUAL");
            String reason = (String) request.getOrDefault("reason", "人工干预重置");
            
            if (ip == null || ip.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP 地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            interventionService.forceResetToNormal(ip, operator, reason);
            
            response.put("success", true);
            response.put("message", "状态已强制重置为NORMAL");
            response.put("ip", ip);
            logger.info("人工干预：强制重置状态, ip={}, operator={}", ip, operator);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("强制重置状态时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/intervention/ban")
    public ResponseEntity<Map<String, Object>> forceBan(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String ip = (String) request.get("ip");
            Long duration = request.get("duration") != null ? 
                ((Number) request.get("duration")).longValue() : null;
            String operator = (String) request.getOrDefault("operator", "MANUAL");
            String reason = (String) request.getOrDefault("reason", "紧急封禁");
            
            if (ip == null || ip.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP 地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            interventionService.forceBan(ip, duration, operator, reason);
            
            long expireTime = duration != null ? 
                System.currentTimeMillis() + duration * 1000 : Long.MAX_VALUE;
            blacklistFilter.addToBlacklist(ip, expireTime);
            
            response.put("success", true);
            response.put("message", "已强制封禁IP");
            response.put("ip", ip);
            response.put("duration", duration);
            logger.info("人工干预：紧急封禁, ip={}, duration={}, operator={}", ip, duration, operator);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("强制封禁时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/intervention/batch-reset")
    public ResponseEntity<Map<String, Object>> batchResetToNormal(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> ips = (java.util.List<String>) request.get("ips");
            String operator = (String) request.getOrDefault("operator", "MANUAL");
            String reason = (String) request.getOrDefault("reason", "批量重置");
            
            if (ips == null || ips.isEmpty()) {
                response.put("success", false);
                response.put("message", "IP 列表不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            interventionService.batchResetToNormal(ips, operator, reason);
            
            response.put("success", true);
            response.put("message", "批量重置成功");
            response.put("count", ips.size());
            logger.info("人工干预：批量重置状态, count={}, operator={}", ips.size(), operator);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("批量重置状态时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/intervention/logs/{ip}")
    public ResponseEntity<Map<String, Object>> getInterventionLogs(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            java.util.List<com.network.gateway.dto.StateInterventionLog> logs = 
                interventionService.getInterventionLogs(ip);
            
            response.put("success", true);
            response.put("ip", ip);
            response.put("logs", logs);
            response.put("count", logs.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取干预日志时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/intervention/stats")
    public ResponseEntity<Map<String, Object>> getInterventionStats() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("stats", interventionService.getStats());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取干预统计时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
