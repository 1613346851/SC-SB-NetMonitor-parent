package com.network.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 跨服务安全配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "cross-service.security")
public class CrossServiceSecurityProperties {

    /**
     * 是否启用安全验证
     */
    private boolean enabled = true;

    /**
     * 签名密钥（双方服务必须一致）
     */
    private String secretKey = "DefaultSecretKeyPleaseChangeInProduction123456";

    /**
     * IP白名单（逗号分隔，支持CIDR格式）
     */
    private String ipWhitelist = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    /**
     * 时间戳容忍范围（毫秒），默认5分钟
     */
    private long timestampTolerance = 300000;

    /**
     * 获取IP白名单列表
     */
    public List<String> getIpWhitelistList() {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(ipWhitelist.split(","));
    }
}
