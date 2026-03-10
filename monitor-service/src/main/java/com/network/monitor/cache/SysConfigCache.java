package com.network.monitor.cache;

import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SysConfigCache {

    @Autowired
    private SysConfigService sysConfigService;

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAllConfigs();
        log.info("系统配置缓存初始化完成");
    }

    public void loadAllConfigs() {
        try {
            sysConfigService.getAllConfigs().forEach(config -> {
                configCache.put(config.getConfigKey(), config.getConfigValue());
            });
            log.info("加载系统配置完成，共{}项", configCache.size());
        } catch (Exception e) {
            log.error("加载系统配置失败", e);
        }
    }

    public String getConfigValue(String configKey) {
        return configCache.get(configKey);
    }

    public String getConfigValue(String configKey, String defaultValue) {
        String value = configCache.get(configKey);
        return value != null ? value : defaultValue;
    }

    public int getIntValue(String configKey, int defaultValue) {
        String value = configCache.get(configKey);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLongValue(String configKey, long defaultValue) {
        String value = configCache.get(configKey);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBooleanValue(String configKey, boolean defaultValue) {
        String value = configCache.get(configKey);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public void refresh() {
        configCache.clear();
        loadAllConfigs();
        log.info("系统配置缓存已刷新");
    }

    public void updateConfig(String configKey, String configValue) {
        configCache.put(configKey, configValue);
    }
}