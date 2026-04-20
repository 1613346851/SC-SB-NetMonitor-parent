package com.network.monitor.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 网关配置同步数据传输对象
 * 用于监测服务与网关服务之间的配置同步
 */
@Data
public class GatewayConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 批量配置（用于批量同步）
     */
    private Map<String, String> configs;

    /**
     * 同步时间戳
     */
    private Long timestamp;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 默认值
     */
    private String defaultValue;

    public GatewayConfigDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public GatewayConfigDTO(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.timestamp = System.currentTimeMillis();
    }

    public GatewayConfigDTO(String configKey, String configValue, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
    }

    public GatewayConfigDTO(Map<String, String> configs) {
        this.configs = configs;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 判断是否为批量配置
     */
    public boolean isBatchConfig() {
        return configs != null && !configs.isEmpty();
    }

    /**
     * 判断是否为单配置
     */
    public boolean isSingleConfig() {
        return configKey != null && !configKey.isEmpty();
    }

    /**
     * 验证配置项是否有效
     */
    public boolean isValid() {
        if (isBatchConfig()) {
            return true;
        }
        return isSingleConfig() && configValue != null;
    }
}
