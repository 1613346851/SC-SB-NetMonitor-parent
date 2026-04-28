package com.network.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.RuleCache;
import com.network.gateway.dto.AttackRuleDTO;
import com.network.gateway.entity.ConfigSyncState;
import com.network.gateway.service.ConfigSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置同步控制器
 * 接收监测服务推送的配置更新，支持配置同步和管理
 *
 * @author network-monitor
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/gateway/config")
public class GatewayConfigSyncController {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfigSyncController.class);

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfigSyncService configSyncService;

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> pushConfig(@RequestBody String requestBody) {
        Map<String, Object> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            
            if (root.has("configs") && root.get("configs").isObject()) {
                JsonNode configsNode = root.get("configs");
                Map<String, String> configs = new HashMap<>();
                
                configsNode.fields().forEachRemaining(entry -> {
                    configs.put(entry.getKey(), entry.getValue().asText());
                });
                
                configCache.updateConfigs(configs);
                logger.info("批量接收配置更新成功，共{}项", configs.size());
            } else if (root.has("configKey") && root.has("configValue")) {
                String configKey = root.get("configKey").asText();
                String configValue = root.get("configValue").asText();
                
                configCache.updateConfig(configKey, configValue);
                logger.info("接收单个配置更新成功: {} = {}", configKey, configValue);
            } else {
                result.put("success", false);
                result.put("message", "无效的配置数据格式");
                return ResponseEntity.badRequest().body(result);
            }

            result.put("success", true);
            result.put("message", "配置更新成功");
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("接收配置更新失败", e);
            result.put("success", false);
            result.put("message", "配置更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncConfigs(@RequestBody(required = false) String requestBody) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> configsToSync = new HashMap<>();
            
            if (requestBody != null && !requestBody.isEmpty()) {
                JsonNode root = objectMapper.readTree(requestBody);
                
                if (root.has("configs") && root.get("configs").isObject()) {
                    JsonNode configsNode = root.get("configs");
                    configsNode.fields().forEachRemaining(entry -> {
                        configsToSync.put(entry.getKey(), entry.getValue().asText());
                    });
                }
            }

            if (!configsToSync.isEmpty()) {
                configCache.updateConfigs(configsToSync);
                logger.info("批量同步配置成功，共{}项", configsToSync.size());
                result.put("syncedCount", configsToSync.size());
            } else {
                boolean success = configCache.pullFromMonitorService();
                if (success) {
                    logger.info("从监测服务同步配置成功");
                } else {
                    logger.warn("从监测服务同步配置失败，使用本地缓存");
                }
                result.put("pulledFromRemote", success);
            }

            result.put("success", true);
            result.put("message", "配置同步成功");
            result.put("totalConfigs", configCache.size());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("配置同步失败", e);
            result.put("success", false);
            result.put("message", "配置同步失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentConfigs() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> configs = configCache.getAllConfigs();
            result.put("success", true);
            result.put("data", configs);
            result.put("count", configs.size());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取当前配置失败", e);
            result.put("success", false);
            result.put("message", "获取当前配置失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = configCache.pullFromMonitorService();
            if (success) {
                result.put("success", true);
                result.put("message", "配置刷新成功");
                result.put("totalConfigs", configCache.size());
                result.put("timestamp", System.currentTimeMillis());
                logger.info("配置刷新成功");
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "配置刷新失败，无法连接监测服务");
                result.put("totalConfigs", configCache.size());
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            logger.error("配置刷新失败", e);
            result.put("success", false);
            result.put("message", "配置刷新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listConfigs() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> configs = configCache.getAllConfigs();
            result.put("success", true);
            result.put("data", configs);
            result.put("count", configs.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取配置列表失败", e);
            result.put("success", false);
            result.put("message", "获取配置列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/value/{configKey}")
    public ResponseEntity<Map<String, Object>> getConfigValue(@PathVariable String configKey) {
        Map<String, Object> result = new HashMap<>();
        try {
            String value = configCache.getString(configKey);
            if (value != null) {
                result.put("success", true);
                result.put("configKey", configKey);
                result.put("configValue", value);
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "配置项不存在: " + configKey);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            logger.error("获取配置值失败: {}", configKey, e);
            result.put("success", false);
            result.put("message", "获取配置值失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("stats", configCache.getStats());
        result.put("count", configCache.size());
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("status", "UP");
        result.put("configCount", configCache.size());
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, Object>> onConfigChange(
            @RequestBody Map<String, Object> notification) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String newVersion = (String) notification.get("version");
            String source = (String) notification.get("source");
            
            logger.info("收到配置变更通知: version={}, source={}", newVersion, source);
            
            boolean triggered = configSyncService.onConfigChangeNotification(newVersion);
            
            result.put("success", true);
            result.put("message", triggered ? "已触发配置同步" : "无需同步");
            result.put("gatewayId", configSyncService.getGatewayId());
            result.put("currentVersion", configSyncService.getCurrentVersion());
            result.put("triggered", triggered);
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("处理配置变更通知失败", e);
            result.put("success", false);
            result.put("message", "处理失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ConfigSyncState state = configSyncService.getSyncState();
            
            if (state != null) {
                result.put("success", true);
                result.put("gatewayId", state.getGatewayId());
                result.put("currentVersion", state.getCurrentVersion());
                result.put("syncStatus", state.getSyncStatus());
                result.put("lastSyncTime", state.getLastSyncTime());
                result.put("failCount", state.getSyncFailCount());
                result.put("handshakeStep", state.getHandshakeStep());
            } else {
                result.put("success", false);
                result.put("message", "无同步状态");
            }
            
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取同步状态失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/sync/force")
    public ResponseEntity<Map<String, Object>> forceSync() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            configSyncService.forceSync();
            
            result.put("success", true);
            result.put("message", "已触发强制同步");
            result.put("gatewayId", configSyncService.getGatewayId());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("强制同步失败", e);
            result.put("success", false);
            result.put("message", "强制同步失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/sync/stats")
    public ResponseEntity<Map<String, Object>> getSyncStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
            result.put("syncStats", configSyncService.getStats());
            result.put("cacheStats", configCache.getStats());
            result.put("configCount", configCache.size());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取同步统计失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/rule/sync")
    public ResponseEntity<Map<String, Object>> syncRule(@RequestBody AttackRuleDTO ruleDTO) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (ruleDTO == null || ruleDTO.getId() == null) {
                result.put("success", false);
                result.put("message", "规则数据无效");
                return ResponseEntity.badRequest().body(result);
            }

            String operation = ruleDTO.getOperation();
            Integer enabled = ruleDTO.getEnabled();
            
            if ("DELETE".equals(operation)) {
                ruleCache.removeRule(ruleDTO.getId());
                logger.info("删除规则成功: id={}", ruleDTO.getId());
            } else if (enabled == null || enabled != 1) {
                ruleCache.removeRule(ruleDTO.getId());
                logger.info("规则已禁用，从缓存移除: id={}, enabled={}", ruleDTO.getId(), enabled);
            } else if ("ADD".equals(operation) || "UPDATE".equals(operation)) {
                ruleCache.addRule(ruleDTO);
                logger.info("同步规则成功: id={}, operation={}", ruleDTO.getId(), operation);
            } else {
                ruleCache.addRule(ruleDTO);
                logger.info("同步规则成功: id={}", ruleDTO.getId());
            }

            result.put("success", true);
            result.put("message", "规则同步成功");
            result.put("ruleId", ruleDTO.getId());
            result.put("totalRules", ruleCache.size());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("规则同步失败", e);
            result.put("success", false);
            result.put("message", "规则同步失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/rule/sync/batch")
    public ResponseEntity<Map<String, Object>> syncRulesBatch(@RequestBody List<AttackRuleDTO> rules) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (rules == null || rules.isEmpty()) {
                result.put("success", false);
                result.put("message", "规则列表为空");
                return ResponseEntity.badRequest().body(result);
            }

            ruleCache.syncRules(rules);

            result.put("success", true);
            result.put("message", "批量同步规则成功");
            result.put("syncedCount", rules.size());
            result.put("totalRules", ruleCache.size());
            result.put("timestamp", System.currentTimeMillis());
            
            logger.info("批量同步规则成功: count={}, totalRules={}", rules.size(), ruleCache.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("批量同步规则失败", e);
            result.put("success", false);
            result.put("message", "批量同步规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @DeleteMapping("/rule/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ruleCache.removeRule(id);
            
            result.put("success", true);
            result.put("message", "规则删除成功");
            result.put("ruleId", id);
            result.put("totalRules", ruleCache.size());
            result.put("timestamp", System.currentTimeMillis());
            
            logger.info("删除规则成功: id={}", id);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("删除规则失败", e);
            result.put("success", false);
            result.put("message", "删除规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/rule/list")
    public ResponseEntity<Map<String, Object>> getRuleList() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<AttackRuleDTO> rules = ruleCache.getAllRules();
            
            result.put("success", true);
            result.put("data", rules);
            result.put("count", rules.size());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取规则列表失败", e);
            result.put("success", false);
            result.put("message", "获取规则列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/rule/stats")
    public ResponseEntity<Map<String, Object>> getRuleStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
            result.put("ruleStats", ruleCache.getDetailedStats());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取规则统计失败", e);
            result.put("success", false);
            result.put("message", "获取规则统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
