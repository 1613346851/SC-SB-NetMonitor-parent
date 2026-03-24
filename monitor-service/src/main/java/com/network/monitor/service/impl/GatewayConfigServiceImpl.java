package com.network.monitor.service.impl;

import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.service.GatewayConfigService;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class GatewayConfigServiceImpl implements GatewayConfigService {

    private static final String GATEWAY_CONFIG_PREFIX = "gateway.";

    private static final List<String> GATEWAY_CONFIG_KEYS = Arrays.asList(
        "gateway.defense.blacklist.enabled",
        "gateway.defense.rate-limit.enabled",
        "gateway.defense.malicious-request.enabled",
        "gateway.defense.rate-limit.default-threshold",
        "gateway.defense.rate-limit.window-size",
        "gateway.defense.blacklist.default-expire-seconds",
        "gateway.defense.malicious.user-agents",
        "gateway.defense.malicious.uri-patterns",
        "gateway.cache.traffic-expire-ms",
        "gateway.cache.blacklist-expire-ms",
        "gateway.cache.cleanup-interval-ms",
        "gateway.attack-state.cooldown-duration-ms",
        "gateway.attack-state.state-expire-ms",
        "gateway.request.max-body-size",
        "gateway.request.abnormal-response-threshold-ms"
    );

    private static final Map<String, String> CONFIG_DESCRIPTIONS = new LinkedHashMap<String, String>() {{
        put("gateway.defense.blacklist.enabled", "黑名单防御开关");
        put("gateway.defense.rate-limit.enabled", "限流防御开关");
        put("gateway.defense.malicious-request.enabled", "恶意请求拦截开关");
        put("gateway.defense.rate-limit.default-threshold", "默认限流阈值(次/秒)");
        put("gateway.defense.rate-limit.window-size", "限流时间窗口(毫秒)");
        put("gateway.defense.blacklist.default-expire-seconds", "黑名单默认过期时间(秒)");
        put("gateway.defense.malicious.user-agents", "恶意User-Agent列表(逗号分隔)");
        put("gateway.defense.malicious.uri-patterns", "恶意URI模式列表(逗号分隔)");
        put("gateway.cache.traffic-expire-ms", "流量缓存过期时间(毫秒)");
        put("gateway.cache.blacklist-expire-ms", "黑名单缓存过期时间(毫秒)");
        put("gateway.cache.cleanup-interval-ms", "缓存清理间隔(毫秒)");
        put("gateway.attack-state.cooldown-duration-ms", "冷却持续时间(毫秒)");
        put("gateway.attack-state.state-expire-ms", "攻击状态过期时间(毫秒)");
        put("gateway.request.max-body-size", "最大请求体大小(字节)");
        put("gateway.request.abnormal-response-threshold-ms", "异常响应时间阈值(毫秒)");
    }};

    private static final Map<String, String> DEFAULT_CONFIG_VALUES = new HashMap<String, String>() {{
        put("gateway.defense.blacklist.enabled", "true");
        put("gateway.defense.rate-limit.enabled", "true");
        put("gateway.defense.malicious-request.enabled", "true");
        put("gateway.defense.rate-limit.default-threshold", "10");
        put("gateway.defense.rate-limit.window-size", "1000");
        put("gateway.defense.blacklist.default-expire-seconds", "600");
        put("gateway.defense.malicious.user-agents", "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
        put("gateway.defense.malicious.uri-patterns", "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
        put("gateway.cache.traffic-expire-ms", "3600000");
        put("gateway.cache.blacklist-expire-ms", "600000");
        put("gateway.cache.cleanup-interval-ms", "60000");
        put("gateway.attack-state.cooldown-duration-ms", "300000");
        put("gateway.attack-state.state-expire-ms", "600000");
        put("gateway.request.max-body-size", "102400");
        put("gateway.request.abnormal-response-threshold-ms", "3000");
    }};

    @Autowired
    private SysConfigCache sysConfigCache;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Override
    public Map<String, Object> getAllGatewayConfigs() {
        Map<String, Object> configs = new LinkedHashMap<>();

        for (String key : GATEWAY_CONFIG_KEYS) {
            String value = sysConfigCache.getConfigValue(key);
            if (value != null) {
                configs.put(key, value);
            } else {
                String defaultValue = DEFAULT_CONFIG_VALUES.get(key);
                if (defaultValue != null) {
                    configs.put(key, defaultValue);
                }
            }
        }

        log.debug("获取所有网关配置，共{}项", configs.size());
        return configs;
    }

    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        String value = sysConfigCache.getConfigValue(configKey);
        if (value != null) {
            return value;
        }
        return DEFAULT_CONFIG_VALUES.getOrDefault(configKey, defaultValue);
    }

    @Override
    public int getIntConfigValue(String configKey, int defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析为整数失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLongConfigValue(String configKey, long defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析为长整数失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanConfigValue(String configKey, boolean defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    @Override
    public List<String> getListConfigValue(String configKey, String defaultValue) {
        String value = getConfigValue(configKey, defaultValue);
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split(","));
    }

    @Override
    public void updateConfig(String configKey, String configValue) {
        if (!configKey.startsWith(GATEWAY_CONFIG_PREFIX)) {
            throw new IllegalArgumentException("非网关配置项: " + configKey);
        }

        sysConfigService.updateConfigValue(configKey, configValue);
        sysConfigCache.updateConfig(configKey, configValue);

        boolean pushSuccess = pushConfigToGateway(configKey, configValue);
        if (pushSuccess) {
            log.info("更新网关配置成功并已推送：configKey={}, configValue={}", configKey, configValue);
        } else {
            log.warn("更新网关配置成功但推送失败：configKey={}, configValue={}", configKey, configValue);
        }
    }

    @Override
    public boolean pushConfigToGateway(String configKey, String configValue) {
        try {
            return gatewayApiClient.pushConfigToGateway(configKey, configValue);
        } catch (Exception e) {
            log.error("推送配置到网关失败: key={}, value={}", configKey, configValue, e);
            return false;
        }
    }

    @Override
    public boolean pushAllConfigsToGateway() {
        try {
            Map<String, Object> configs = getAllGatewayConfigs();
            return gatewayApiClient.pushConfigsToGateway(configs);
        } catch (Exception e) {
            log.error("推送所有配置到网关失败", e);
            return false;
        }
    }

    @Override
    public void refreshGatewayConfigCache() {
        sysConfigCache.refresh();
        boolean pushSuccess = pushAllConfigsToGateway();
        if (pushSuccess) {
            log.info("刷新网关配置缓存成功并已推送所有配置");
        } else {
            log.warn("刷新网关配置缓存成功但推送失败");
        }
    }

    @Override
    public List<Map<String, Object>> getGatewayConfigList() {
        List<Map<String, Object>> configList = new ArrayList<>();

        for (String key : GATEWAY_CONFIG_KEYS) {
            Map<String, Object> configItem = new LinkedHashMap<>();
            configItem.put("configKey", key);
            configItem.put("configValue", getConfigValue(key, ""));
            configItem.put("description", CONFIG_DESCRIPTIONS.getOrDefault(key, ""));
            configItem.put("defaultValue", DEFAULT_CONFIG_VALUES.getOrDefault(key, ""));
            configList.add(configItem);
        }

        return configList;
    }

    @Override
    public int getGatewayConfigCount() {
        int count = 0;
        for (String key : GATEWAY_CONFIG_KEYS) {
            String value = sysConfigCache.getConfigValue(key);
            if (value != null) {
                count++;
            }
        }
        return count;
    }
}
