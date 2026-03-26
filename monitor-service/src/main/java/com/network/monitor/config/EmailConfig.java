package com.network.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert.email")
public class EmailConfig {

    private Boolean enabled = false;

    private String smtpHost;

    private Integer smtpPort = 465;

    private String smtpUsername;

    private String smtpPassword;

    private Boolean sslEnabled = true;

    private String fromAddress;

    private String toAddresses;
}
