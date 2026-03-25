package com.network.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.gateway.constant.GatewayHttpConstant;
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

/**
 * 监控服务防御日志推送客户端
 * 负责将防御执行日志推送到监控服务
 *
 * @author network-monitor
 * @since 1.0.0
 */
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

    /**
     * 推送防御日志到监控服务
     * 添加跨服务鉴权头，确保调用安全性
     *
     * @param defenseLogDTO 防御日志 DTO
     * @throws RestClientException 网络异常
     */
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

    /**
     * 检查响应是否成功
     * 不仅检查 HTTP 状态码，还检查响应体中的 code 字段
     */
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
     * 批量推送防御日志（简化版）
     *
     * @param defenseLogDTOs 防御日志DTO集合
     * @return 成功推送的数量
     */
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

    /**
     * 异步推送防御日志
     *
     * @param defenseLogDTO 防御日志DTO
     */
    public void pushDefenseLogAsync(DefenseLogDTO defenseLogDTO) {
        new Thread(() -> {
            try {
                pushDefenseLog(defenseLogDTO);
            } catch (Exception e) {
                logger.warn("异步推送防御日志失败: 防御对象[{}]", defenseLogDTO.getDefenseTarget(), e);
            }
        }, "defense-log-push-" + System.currentTimeMillis()).start();
    }

    /**
     * 带重试机制的防御日志推送
     *
     * @param defenseLogDTO 防御日志DTO
     * @param maxRetries 最大重试次数
     * @return true表示推送成功
     */
    public boolean pushDefenseLogWithRetry(DefenseLogDTO defenseLogDTO, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                pushDefenseLog(defenseLogDTO);
                return true;
            } catch (Exception e) {
                logger.warn("第{}次推送防御日志失败: 防御对象[{}], 错误: {}", 
                           i + 1, defenseLogDTO.getDefenseTarget(), e.getMessage());
                
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
        
        logger.error("防御日志推送最终失败，已达到最大重试次数: 防御对象[{}]", defenseLogDTO.getDefenseTarget());
        return false;
    }

    /**
     * 检查监控服务连通性
     *
     * @return true表示服务可达
     */
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

    /**
     * 获取客户端统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return "防御日志推送客户端 - 配置端点：" + GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT;
    }

    /**
     * 推送DDoS攻击事件到监控服务
     * 当IP连续触发限流达到阈值时，自动推送DDoS攻击事件
     *
     * @param sourceIp 源IP地址
     * @param rateLimitCount 限流触发次数
     * @param httpMethod HTTP方法
     * @param requestUri 请求URI
     * @param userAgent 用户代理
     * @throws RestClientException 网络异常
     */
    public void pushDDoSAttackEvent(String sourceIp, int rateLimitCount, 
                                    String httpMethod, String requestUri, String userAgent) throws RestClientException {
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

            java.util.Map<String, Object> eventBody = new java.util.HashMap<>();
            eventBody.put("sourceIp", sourceIp);
            eventBody.put("attackType", "DDOS");
            eventBody.put("riskLevel", "HIGH");
            eventBody.put("confidence", 85);
            eventBody.put("rateLimitCount", rateLimitCount);
            eventBody.put("httpMethod", httpMethod);
            eventBody.put("requestUri", requestUri);
            eventBody.put("userAgent", userAgent);
            eventBody.put("description", String.format("连续触发限流%d次，自动升级为DDoS攻击", rateLimitCount));
            eventBody.put("timestamp", System.currentTimeMillis());

            HttpEntity<java.util.Map<String, Object>> requestEntity = new HttpEntity<>(eventBody, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.DDOS_ATTACK_EVENT_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}", sourceIp, rateLimitCount);
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("DDoS攻击事件推送失败: ip={}, 响应内容[{}]", sourceIp, errorMsg);
                throw new RestClientException("监控服务返回错误: " + errorMsg);
            }

        } catch (RestClientException e) {
            logger.error("推送DDoS攻击事件到监控服务失败: ip={}, 错误: {}", sourceIp, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送DDoS攻击事件时发生未知异常: ip={}", sourceIp, e);
            throw new RestClientException("推送DDoS攻击事件时发生未知异常", e);
        }
    }

    /**
     * 推送黑名单事件到监控服务
     * 当IP被确认攻击并执行防御时，推送黑名单事件
     *
     * @param sourceIp 源IP地址
     * @param banType 封禁类型（SYSTEM/MANUAL）
     * @param banReason 封禁原因
     * @param duration 封禁时长（秒，null表示永久）
     * @throws RestClientException 网络异常
     */
    public void pushBlacklistEvent(String sourceIp, String banType, String banReason, Long duration) throws RestClientException {
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

            java.util.Map<String, Object> eventBody = new java.util.HashMap<>();
            eventBody.put("ip", sourceIp);
            eventBody.put("banType", banType != null ? banType : "SYSTEM");
            eventBody.put("banReason", banReason);
            eventBody.put("duration", duration);
            eventBody.put("operator", "SYSTEM");
            eventBody.put("timestamp", System.currentTimeMillis());

            HttpEntity<java.util.Map<String, Object>> requestEntity = new HttpEntity<>(eventBody, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.BLACKLIST_EVENT_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (isResponseSuccessful(response)) {
                logger.info("黑名单事件推送成功: ip={}, banType={}, reason={}", sourceIp, banType, banReason);
            } else {
                String errorMsg = extractErrorMessage(response.getBody());
                logger.error("黑名单事件推送失败: ip={}, 响应内容[{}]", sourceIp, errorMsg);
                throw new RestClientException("监控服务返回错误: " + errorMsg);
            }

        } catch (RestClientException e) {
            logger.error("推送黑名单事件到监控服务失败: ip={}, 错误: {}", sourceIp, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送黑名单事件时发生未知异常: ip={}", sourceIp, e);
            throw new RestClientException("推送黑名单事件时发生未知异常", e);
        }
    }
}
