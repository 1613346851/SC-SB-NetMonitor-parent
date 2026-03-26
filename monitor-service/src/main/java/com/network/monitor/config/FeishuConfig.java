package com.network.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert.feishu")
public class FeishuConfig {

    private Boolean enabled = false;

    private String webhookUrl;

    private String secret;
}
