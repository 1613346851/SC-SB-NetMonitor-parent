package com.network.gateway.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.gateway.dto.AttackRuleDTO;
import com.network.gateway.dto.WhitelistDTO;
import com.network.gateway.util.CrossServiceSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MonitorServiceRuleClient {

    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceRuleClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String RULE_LIST_ENDPOINT = "/api/inner/gateway/rules";

    private static final String WHITELIST_LIST_ENDPOINT = "/api/inner/gateway/whitelists";

    @Value("${cross-service.auth.monitor-token:MonitorSecureToken456}")
    private String monitorToken;

    @Value("${cross-service.security.secret-key:NetMonitor2026CrossServiceSecretKey!@#$%}")
    private String secretKey;

    @Value("${gateway.monitor-service.url:http://localhost:9002}")
    private String monitorServiceUrl;

    public List<AttackRuleDTO> pullRulesFromMonitor() {
        try {
            String url = monitorServiceUrl + RULE_LIST_ENDPOINT;

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), 
                        new TypeReference<Map<String, Object>>() {});
                
                Integer code = (Integer) result.get("code");
                if (code != null && code == 200) {
                    List<AttackRuleDTO> rules = objectMapper.convertValue(
                            result.get("data"), 
                            new TypeReference<List<AttackRuleDTO>>() {});
                    
                    logger.info("从监控服务拉取规则成功: count={}", rules != null ? rules.size() : 0);
                    return rules;
                } else {
                    String message = (String) result.get("message");
                    logger.warn("从监控服务拉取规则失败: code={}, message={}", code, message);
                    return null;
                }
            }

            logger.warn("从监控服务拉取规则失败: statusCode={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            logger.error("从监控服务拉取规则异常: {}", e.getMessage());
            return null;
        }
    }

    public List<WhitelistDTO> pullWhitelistsFromMonitor() {
        try {
            String url = monitorServiceUrl + WHITELIST_LIST_ENDPOINT;

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), 
                        new TypeReference<Map<String, Object>>() {});
                
                Integer code = (Integer) result.get("code");
                if (code != null && code == 200) {
                    List<WhitelistDTO> whitelists = objectMapper.convertValue(
                            result.get("data"), 
                            new TypeReference<List<WhitelistDTO>>() {});
                    
                    logger.info("从监控服务拉取白名单成功: count={}", whitelists != null ? whitelists.size() : 0);
                    return whitelists;
                } else {
                    String message = (String) result.get("message");
                    logger.warn("从监控服务拉取白名单失败: code={}, message={}", code, message);
                    return null;
                }
            }

            logger.warn("从监控服务拉取白名单失败: statusCode={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            logger.error("从监控服务拉取白名单异常: {}", e.getMessage());
            return null;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> securityHeaders = CrossServiceSecurityUtil.generateSecurityHeaders(
                requestId,
                secretKey
        );
        
        headers.set("X-Timestamp", securityHeaders.get("X-Timestamp"));
        headers.set("X-Request-ID", securityHeaders.get("X-Request-ID"));
        headers.set("X-Signature", securityHeaders.get("X-Signature"));
        headers.set("X-Source-IP", "gateway-service");
        
        return headers;
    }
}
