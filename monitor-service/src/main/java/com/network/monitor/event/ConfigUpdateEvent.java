package com.network.monitor.event;

import org.springframework.context.ApplicationEvent;

public class ConfigUpdateEvent extends ApplicationEvent {
    
    private final String configKey;
    private final String configValue;
    
    public ConfigUpdateEvent(Object source, String configKey, String configValue) {
        super(source);
        this.configKey = configKey;
        this.configValue = configValue;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public String getConfigValue() {
        return configValue;
    }
}
