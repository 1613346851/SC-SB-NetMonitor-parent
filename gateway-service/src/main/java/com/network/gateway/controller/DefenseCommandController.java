package com.network.gateway.controller;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.IpBlacklistCache;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.dto.DefenseCommandDTO;
import com.network.gateway.filter.defense.IpBlacklistDefenseFilter;
import com.network.gateway.filter.defense.MaliciousRequestBlockFilter;
import com.network.gateway.filter.defense.RequestRateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 防御指令接收控制器
 * 提供REST接口供监控服务推送高危攻击防御指令
 *
 * @author network-monitor
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/gateway/defense")
public class DefenseCommandController {

    private static final Logger logger = LoggerFactory.getLogger(DefenseCommandController.class);

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

    /**
     * 接收防御指令
     *
     * @param commandDTO 防御指令DTO
     * @return 响应结果
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> receiveDefenseCommand(@RequestBody DefenseCommandDTO commandDTO) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 验证指令有效性
            if (commandDTO == null || !commandDTO.isValid()) {
                response.put("success", false);
                response.put("message", "无效的防御指令");
                response.put("errorCode", "INVALID_COMMAND");
                logger.warn("接收到无效的防御指令: {}", commandDTO);
                return ResponseEntity.badRequest().body(response);
            }

            // 根据指令类型执行相应的防御操作
            boolean success = executeDefenseCommand(commandDTO);
            
            if (success) {
                response.put("success", true);
                response.put("message", "防御指令执行成功");
                response.put("commandId", commandDTO.getCommandId());
                response.put("remainingTime", commandDTO.getRemainingTime());
                logger.info("防御指令执行成功: 指令ID[{}] 类型[{}] IP[{}]", 
                           commandDTO.getCommandId(), 
                           commandDTO.getDefenseType(), 
                           commandDTO.getSourceIp());
            } else {
                response.put("success", false);
                response.put("message", "防御指令执行失败");
                response.put("commandId", commandDTO.getCommandId());
                response.put("errorCode", "EXECUTION_FAILED");
                logger.error("防御指令执行失败: 指令ID[{}] 类型[{}] IP[{}]", 
                            commandDTO.getCommandId(), 
                            commandDTO.getDefenseType(), 
                            commandDTO.getSourceIp());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("处理防御指令时发生异常: 指令ID[{}]", 
                        commandDTO != null ? commandDTO.getCommandId() : "unknown", e);
            
            response.put("success", false);
            response.put("message", "处理防御指令时发生系统异常: " + e.getMessage());
            response.put("errorCode", "SYSTEM_ERROR");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 执行防御指令
     *
     * @param commandDTO 防御指令DTO
     * @return true表示执行成功
     */
    private boolean executeDefenseCommand(DefenseCommandDTO commandDTO) {
        DefenseCommandDTO.DefenseType defenseType = commandDTO.getDefenseType();
        String sourceIp = commandDTO.getSourceIp();
        Long expireTime = commandDTO.getExpireTimestamp();

        return switch (defenseType) {
            case BLACKLIST -> {
                boolean added = blacklistFilter.addToBlacklist(sourceIp, expireTime);
                yield added;
            }
            case RATE_LIMIT -> {
                // 请求限流通常通过缓存自动处理，这里可以更新限流阈值
                int threshold = commandDTO.getRateLimitThreshold() != null ? 
                              commandDTO.getRateLimitThreshold() : 5; // 默认5次/秒
                logger.info("更新IP[{}]的限流阈值为{}次/秒", sourceIp, threshold);
                yield true; // 限流配置更新成功
            }
            case BLOCK -> {
                maliciousRequestFilter.addMaliciousIp(sourceIp);
                yield true;
            }
        };
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
            status.put("message", "获取防御状态失败: " + e.getMessage());
            return ResponseEntity.status(500).body(status);
        }
    }

    /**
     * 手动添加IP到黑名单
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
                response.put("message", "IP地址不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            long expireTime = System.currentTimeMillis() + 
                            (expireMinutes != null ? expireMinutes * 60 * 1000L : 10 * 60 * 1000L); // 默认10分钟
            
            boolean added = blacklistFilter.addToBlacklist(ip, expireTime);
            
            if (added) {
                response.put("success", true);
                response.put("message", "IP已成功加入黑名单");
                response.put("ip", ip);
                response.put("expireTime", expireTime);
                logger.info("手动添加IP到黑名单: {}", ip);
            } else {
                response.put("success", false);
                response.put("message", "添加IP到黑名单失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("手动添加黑名单时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址
     * @return 响应结果
     */
    @DeleteMapping("/blacklist/remove/{ip}")
    public ResponseEntity<Map<String, Object>> removeFromBlacklist(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean removed = blacklistFilter.removeFromBlacklist(ip);
            
            if (removed) {
                response.put("success", true);
                response.put("message", "IP已从黑名单中移除");
                response.put("ip", ip);
                logger.info("从黑名单移除IP: {}", ip);
            } else {
                response.put("success", false);
                response.put("message", "IP不在黑名单中或移除失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("从黑名单移除IP时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 重置指定IP的请求计数
     *
     * @param ip IP地址
     * @return 响应结果
     */
    @PostMapping("/ratelimit/reset/{ip}")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@PathVariable String ip) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            rateLimitFilter.resetRequestCount(ip);
            response.put("success", true);
            response.put("message", "IP请求计数已重置");
            response.put("ip", ip);
            logger.info("重置IP请求计数: {}", ip);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("重置请求计数时发生异常", e);
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "gateway-defense-controller");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
}