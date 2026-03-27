package com.network.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.gateway.constant.GatewayHttpConstant;
import com.network.gateway.dto.BlacklistEventDTO;
import com.network.gateway.dto.DDoSAttackEventDTO;
import com.network.gateway.dto.DefenseLogDTO;
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

import java.util.Map;
import java.util.UUID;

@Component
public class MonitorServiceDefenseClient {

    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceDefenseClient.class);

    @Autowired
    @Qualifier("restTemplate")
    private RestTemplate restTemplate;

    @Value("${cross-service.security.secret-key}")
    private String secretKey;

    @Autowired
    private ObjectMapper objectMapper;

    public void pushDefenseLog(DefenseLogDTO defenseLogDTO) throws RestClientException {
        if (defenseLogDTO == null) {
            logger.warn("尝试推送空的防御日志");
            return;
        }

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

            HttpEntity<DefenseLogDTO> requestEntity = new HttpEntity<>(defenseLogDTO, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                logger.info("防御日志推送成功: 防御类型[{}] 防御对象[{}] 状态码[{}]", 
                           defenseLogDTO.getDefenseType(),
                           defenseLogDTO.getDefenseTarget(),
                           response.getStatusCode());
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("防御日志推送失败: 防御类型[{}] 响应内容[{}]", 
                            defenseLogDTO.getDefenseType(), errorMsg);
                throw new RestClientException("监控服务返回错误: " + errorMsg);
            }

        } catch (RestClientException e) {
            logger.error("推送防御日志到监控服务失败: 防御类型[{}] 防御对象[{}] 错误: {}", 
                        defenseLogDTO.getDefenseType(),
                        defenseLogDTO.getDefenseTarget(),
                        e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送防御日志时发生未知异常: 防御类型[{}]", defenseLogDTO.getDefenseType(), e);
            throw new RestClientException("推送防御日志时发生未知异常", e);
        }
    }

    private boolean isResponseSuccessful(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return false;
        }

        String body = response.getBody();
        if (body == null || body.isEmpty()) {
            return true;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.has("code") ? root.get("code").asInt() : 200;
            return code == 200;
        } catch (Exception e) {
            logger.debug("解析响应体失败，默认认为成功: {}", e.getMessage());
            return true;
        }
    }

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

    public int batchPushDefenseLogs(java.util.List<DefenseLogDTO> defenseLogDTOs) {
        if (defenseLogDTOs == null || defenseLogDTOs.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (DefenseLogDTO defenseLogDTO : defenseLogDTOs) {
            try {
                pushDefenseLog(defenseLogDTO);
                successCount++;
            } catch (Exception e) {
                logger.warn("批量推送中单个防御日志失败: 防御对象[{}]", defenseLogDTO.getDefenseTarget(), e);
            }
        }

        logger.info("批量推送防御日志完成: 总数{} 成功{} 失败{}", 
                   defenseLogDTOs.size(), successCount, defenseLogDTOs.size() - successCount);

        return successCount;
    }

    public void pushDefenseLogAsync(DefenseLogDTO defenseLogDTO) {
        new Thread(() -> {
            try {
                pushDefenseLog(defenseLogDTO);
            } catch (Exception e) {
                logger.warn("异步推送防御日志失败: 防御对象[{}]", defenseLogDTO.getDefenseTarget(), e);
            }
        }, "defense-log-push-" + System.currentTimeMillis()).start();
    }

    public boolean pushDefenseLogWithRetry(DefenseLogDTO defenseLogDTO, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                pushDefenseLog(defenseLogDTO);
                return true;
            } catch (Exception e) {
                logger.warn("第{}次推送防御日志失败: 防御对象[{}], 错误: {}", 
                           i + 1, defenseLogDTO.getDefenseTarget(), e.getMessage());
                
                if (i < maxRetries) {
                    defenseLogDTO.incrementRetryCount();
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        logger.error("防御日志推送最终失败，已达到最大重试次数: 防御对象[{}]", defenseLogDTO.getDefenseTarget());
        return false;
    }

    public boolean checkMonitorServiceConnectivity() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
    
            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    entity,
                    String.class
            );
    
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.debug("监控服务防御日志接口连通性检查失败：{}", e.getMessage());
            return false;
        }
    }

    public String getStatistics() {
        return "防御日志推送客户端 - 配置端点：" + GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT;
    }

    public void pushDDoSAttackEvent(DDoSAttackEventDTO event) throws RestClientException {
        if (event == null) {
            logger.warn("尝试推送空的DDoS攻击事件");
            return;
        }

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

            HttpEntity<DDoSAttackEventDTO> requestEntity = new HttpEntity<>(event, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DDOS_ATTACK_EVENT_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}", event.getSourceIp(), event.getRateLimitCount());
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("DDoS攻击事件推送失败: ip={}, 响应内容[{}]", event.getSourceIp(), errorMsg);
                throw new RestClientException("监控服务返回错误: " + errorMsg);
            }

        } catch (RestClientException e) {
            logger.error("推送DDoS攻击事件到监控服务失败: ip={}, 错误: {}", event.getSourceIp(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送DDoS攻击事件时发生未知异常: ip={}", event.getSourceIp(), e);
            throw new RestClientException("推送DDoS攻击事件时发生未知异常", e);
        }
    }

    public void pushDDoSAttackEvent(String sourceIp, int rateLimitCount, 
                                    String httpMethod, String requestUri, String userAgent) throws RestClientException {
        DDoSAttackEventDTO event = new DDoSAttackEventDTO(sourceIp, rateLimitCount);
        event.setHttpMethod(httpMethod);
        event.setRequestUri(requestUri);
        event.setUserAgent(userAgent);
        pushDDoSAttackEvent(event);
    }

    public void pushBlacklistEvent(BlacklistEventDTO event) throws RestClientException {
        if (event == null) {
            logger.warn("尝试推送空的黑名单事件");
            return;
        }

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

            HttpEntity<BlacklistEventDTO> requestEntity = new HttpEntity<>(event, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.BLACKLIST_EVENT_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                logger.info("黑名单事件推送成功: ip={}, banType={}, reason={}", event.getIp(), event.getBanType(), event.getBanReason());
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("黑名单事件推送失败: ip={}, 响应内容[{}]", event.getIp(), errorMsg);
                throw new RestClientException("监控服务返回错误: " + errorMsg);
            }

        } catch (RestClientException e) {
            logger.error("推送黑名单事件到监控服务失败: ip={}, 错误: {}", event.getIp(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送黑名单事件时发生未知异常: ip={}", event.getIp(), e);
            throw new RestClientException("推送黑名单事件时发生未知异常", e);
        }
    }

    public void pushBlacklistEvent(String sourceIp, String banType, String banReason, Long duration) throws RestClientException {
        BlacklistEventDTO event = new BlacklistEventDTO(sourceIp, banType, banReason);
        event.setDurationSeconds(duration);
        pushBlacklistEvent(event);
    }

    public boolean pushBlacklistEventWithRetry(BlacklistEventDTO event, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                pushBlacklistEvent(event);
                return true;
            } catch (Exception e) {
                logger.warn("第{}次推送黑名单事件失败: ip={}, 错误: {}", 
                           i + 1, event.getIp(), e.getMessage());
                
                if (i < maxRetries) {
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        logger.error("黑名单事件推送最终失败，已达到最大重试次数: ip={}", event.getIp());
        return false;
    }
}
