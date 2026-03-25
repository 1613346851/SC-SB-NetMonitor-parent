package com.network.gateway.cache;

import com.network.gateway.client.MonitorServiceConfigClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关配置缓存
 * 从监控服务拉取配置并缓存到本地
 * 支持动态更新配置
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class GatewayConfigCache {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfigCache.class);

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @Autowired
    private MonitorServiceConfigClient configClient;

    @PostConstruct
    public void init() {
        logger.info("初始化网关配置缓存...");
        loadDefaultConfigs();
        pullFromMonitorService();
        logger.info("网关配置缓存初始化完成，共{}项配置", configCache.size());
    }

    @PreDestroy
    public void destroy() {
        configCache.clear();
        logger.info("网关配置缓存已清理");
    }

    private void loadDefaultConfigs() {
        configCache.put("gateway.defense.blacklist.enabled", "true");
        configCache.put("gateway.defense.rate-limit.enabled", "true");
        configCache.put("gateway.defense.malicious-request.enabled", "true");
        configCache.put("gateway.defense.rate-limit.default-threshold", "10");
        configCache.put("gateway.defense.rate-limit.window-size", "1000");
        configCache.put("gateway.defense.blacklist.default-expire-seconds", "600");
        configCache.put("gateway.defense.malicious.user-agents", 
                "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
        configCache.put("gateway.defense.malicious.uri-patterns", 
                "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
        configCache.put("gateway.cache.traffic-expire-ms", "3600000");
        configCache.put("gateway.cache.blacklist-expire-ms", "600000");
        configCache.put("gateway.cache.cleanup-interval-ms", "60000");
        configCache.put("gateway.attack-state.cooldown-duration-ms", "300000");
        configCache.put("gateway.attack-state.state-expire-ms", "600000");
        configCache.put("gateway.request.max-body-size", "102400");
        configCache.put("gateway.request.abnormal-response-threshold-ms", "3000");

        configCache.put("traffic.push.normal.strategy", "realtime");
        configCache.put("traffic.push.suspicious.strategy", "sampling");
        configCache.put("traffic.push.attacking.strategy", "batch");
        configCache.put("traffic.push.defended.strategy", "skip");
        configCache.put("traffic.push.cooldown.strategy", "sampling");
        configCache.put("traffic.push.batch-interval-ms", "5000");
        configCache.put("traffic.push.sampling-rate", "10");
        configCache.put("traffic.push.enabled", "true");

        configCache.put("ddos.threshold", "20");
        configCache.put("ddos.detection.window-ms", "1000");
        configCache.put("ddos.rate-limit-trigger-count", "3");
        configCache.put("ddos.rate-limit-trigger-window-seconds", "60");

        logger.info("加载默认配置完成，共{}项", configCache.size());
    }

    public boolean pullFromMonitorService() {
        try {
            Map<String, String> remoteConfigs = configClient.pullAllConfigs();
            if (remoteConfigs != null && !remoteConfigs.isEmpty()) {
                int updateCount = 0;
                for (Map.Entry<String, String> entry : remoteConfigs.entrySet()) {
                    String key = entry.getKey();
                    if (key != null && isValidConfigKey(key)) {
                        configCache.put(key, entry.getValue());
                        updateCount++;
                    }
                }
                logger.info("从监控服务拉取配置成功，更新{}项", updateCount);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("从监控服务拉取配置失败，使用本地默认配置: {}", e.getMessage());
            return false;
        }
    }

    private boolean isValidConfigKey(String key) {
        return key.startsWith("gateway.") ||
               key.startsWith("traffic.push.") ||
               key.startsWith("ddos.") ||
               key.startsWith("defense.");
    }

    public void updateConfig(String configKey, String configValue) {
        if (configKey == null || configKey.isEmpty()) {
            logger.warn("尝试更新空配置键");
            return;
        }

        if (!isValidConfigKey(configKey)) {
            logger.warn("非网关配置项，忽略更新: {}", configKey);
            return;
        }

        configCache.put(configKey, configValue);
        logger.info("配置已更新: {} = {}", configKey, configValue);
    }

    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }

        int updateCount = 0;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            if (key != null && isValidConfigKey(key)) {
                configCache.put(key, entry.getValue());
                updateCount++;
            }
        }

        logger.info("批量更新配置完成，共{}项", updateCount);
    }

    public String getString(String configKey) {
        return configCache.get(configKey);
    }

    public String getString(String configKey, String defaultValue) {
        String value = configCache.get(configKey);
        return value != null ? value : defaultValue;
    }

    public int getInt(String configKey, int defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值转换整数失败: {} = {}, 使用默认值: {}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String configKey, long defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值转换长整数失败: {} = {}, 使用默认值: {}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String configKey, boolean defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public List<String> getList(String configKey, String defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(value.split(","));
    }

    public List<String> getList(String configKey) {
        return getList(configKey, "");
    }

    public boolean isDefenseEnabled(String defenseType) {
        String configKey = "gateway.defense." + defenseType + ".enabled";
        return getBoolean(configKey, true);
    }

    public int getRateLimitThreshold() {
        return getInt("gateway.defense.rate-limit.default-threshold", 10);
    }

    public int getBlacklistExpireSeconds() {
        return getInt("gateway.defense.blacklist.default-expire-seconds", 600);
    }

    public long getTrafficCacheExpireMs() {
        return getLong("gateway.cache.traffic-expire-ms", 3600000L);
    }

    public long getBlacklistCacheExpireMs() {
        return getLong("gateway.cache.blacklist-expire-ms", 600000L);
    }

    public long getCacheCleanupIntervalMs() {
        return getLong("gateway.cache.cleanup-interval-ms", 60000L);
    }

    public int getMaxRequestBodySize() {
        return getInt("gateway.request.max-body-size", 102400);
    }

    public long getAbnormalResponseThresholdMs() {
        return getLong("gateway.request.abnormal-response-threshold-ms", 3000L);
    }

    public List<String> getMaliciousUserAgents() {
        return getList("gateway.defense.malicious.user-agents", 
                "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
    }

    public List<String> getMaliciousUriPatterns() {
        return getList("gateway.defense.malicious.uri-patterns",
                "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
    }

    public int getTrafficPushSamplingRate() {
        return getInt("traffic.push.sampling-rate", 10);
    }

    public long getTrafficPushBatchIntervalMs() {
        return getLong("traffic.push.batch-interval-ms", 5000L);
    }

    public boolean isTrafficPushEnabled() {
        return getBoolean("traffic.push.enabled", true);
    }

    public String getTrafficPushStrategy(String state) {
        return getString("traffic.push." + state.toLowerCase() + ".strategy", "realtime");
    }

    public int getDdosThreshold() {
        return getInt("ddos.threshold", 20);
    }

    public long getDdosDetectionWindowMs() {
        return getLong("ddos.detection.window-ms", 1000L);
    }

    public int getDdosRateLimitTriggerCount() {
        return getInt("ddos.rate-limit-trigger-count", 3);
    }

    public int getDdosRateLimitTriggerWindowSeconds() {
        return getInt("ddos.rate-limit-trigger-window-seconds", 60);
    }

    public int size() {
        return configCache.size();
    }

    public Map<String, String> getAllConfigs() {
        return Map.copyOf(configCache);
    }

    public void refresh() {
        logger.info("刷新网关配置缓存...");
        pullFromMonitorService();
    }

    public String getStats() {
        return String.format("网关配置缓存统计 - 总数:%d", configCache.size());
    }

    public boolean containsKey(String configKey) {
        return configCache.containsKey(configKey);
    }
}
