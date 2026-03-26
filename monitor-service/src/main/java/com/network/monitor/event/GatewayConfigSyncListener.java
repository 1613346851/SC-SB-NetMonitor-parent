package com.network.monitor.event;

import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.service.GatewayConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayConfigSyncListener {

    @Autowired
    private GatewayConfigService gatewayConfigService;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Async
    @EventListener
    public void onConfigUpdate(ConfigUpdateEvent event) {
        String configKey = event.getConfigKey();
        String configValue = event.getConfigValue();

        if (!isGatewayConfig(configKey)) {
            return;
        }

        log.info("检测到网关配置变更，自动推送到网关：configKey={}", configKey);

        try {
            boolean success = gatewayConfigService.pushConfigToGateway(configKey, configValue);
            if (success) {
                log.info("网关配置自动推送成功：configKey={}", configKey);
            } else {
                log.warn("网关配置自动推送失败：configKey={}", configKey);
            }
        } catch (Exception e) {
            log.error("网关配置自动推送异常：configKey={}", configKey, e);
        }
    }

    private boolean isGatewayConfig(String configKey) {
        return configKey != null && configKey.startsWith("gateway.");
    }
}
