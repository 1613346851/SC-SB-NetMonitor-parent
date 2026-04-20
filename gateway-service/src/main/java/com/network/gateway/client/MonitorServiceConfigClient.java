package com.network.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.gateway.constant.GatewayHttpConstant;
import com.network.gateway.util.CrossServiceSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 监控服务配置客户端
 * 负责从监控服务拉取网关配置
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class MonitorServiceConfigClient {

    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceConfigClient.class);

    private static final String CONFIG_SYNC_ENDPOINT = "/api/inner/gateway/config/sync";

    @Autowired
    @Qualifier("restTemplate")
    private RestTemplate restTemplate;

    @Value("${cross-service.security.secret-key}")
    private String secretKey;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 从监控服务拉取所有网关配置
     *
     * @return 配置Map，失败时返回null
     */
    public Map<String, String> pullAllConfigs() {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> securityHeaders = CrossServiceSecurityUtil.generateSecurityHeaders(
                    requestId,
                    secretKey
            );
            headers.set("X-Timestamp", securityHeaders.get("X-Timestamp"));
            headers.set("X-Request-ID", securityHeaders.get("X-Request-ID"));
            headers.set("X-Signature", securityHeaders.get("X-Signature"));

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + CONFIG_SYNC_ENDPOINT;
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                Map<String, String> configs = parseConfigsFromResponse(response.getBody());
                logger.info("从监控服务拉取配置成功，共{}项", configs.size());
                return configs;
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("从监控服务拉取配置失败: {}", errorMsg);
                return null;
            }

        } catch (RestClientException e) {
            logger.error("从监控服务拉取配置失败，网络异常: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("从监控服务拉取配置时发生未知异常", e);
            return null;
        }
    }

    /**
     * 检查监控服务配置接口连通性
     *
     * @return true表示服务可达
     */
    public boolean checkConnectivity() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> securityHeaders = CrossServiceSecurityUtil.generateSecurityHeaders(
                    "health-check",
                    secretKey
            );
            headers.set("X-Timestamp", securityHeaders.get("X-Timestamp"));
            headers.set("X-Request-ID", securityHeaders.get("X-Request-ID"));
            headers.set("X-Signature", securityHeaders.get("X-Signature"));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + CONFIG_SYNC_ENDPOINT;
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.debug("监控服务配置接口连通性检查失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查响应是否成功
     */
    private boolean isResponseSuccessful(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return false;
        }

        String body = response.getBody();
        if (body == null || body.isEmpty()) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.has("code") ? root.get("code").asInt() : 200;
            return code == 200;
        } catch (Exception e) {
            logger.debug("解析响应体失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从响应体中解析配置
     */
    private Map<String, String> parseConfigsFromResponse(String body) {
        Map<String, String> configs = new HashMap<>();

        if (body == null || body.isEmpty()) {
            return configs;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.get("data");

            if (dataNode != null && dataNode.isObject()) {
                dataNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().asText();
                    configs.put(key, value);
                });
            }
        } catch (Exception e) {
            logger.error("解析配置响应失败: {}", e.getMessage());
        }

        return configs;
    }

    /**
     * 从响应体中提取错误信息
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isEmpty()) {
            return "未知错误";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.has("message") ? root.get("message").asText() : "未知错误";
            int code = root.has("code") ? root.get("code").asInt() : 500;
            return String.format("code=%d, message=%s", code, message);
        } catch (Exception e) {
            return body;
        }
    }

    /**
     * 获取客户端统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return "监控服务配置客户端 - 配置端点：" + GatewayHttpConstant.MonitorService.BASE_URL + CONFIG_SYNC_ENDPOINT;
    }
}
