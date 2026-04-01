package com.network.monitor.client;

import com.network.monitor.dto.RuleSyncDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 网关规则同步客户端
 * 负责将规则变更推送到网关服务
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Slf4j
@Component
public class MonitorServiceRuleClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String GATEWAY_BASE_URL = "http://localhost:9000";

    private static final String RULE_SYNC_ENDPOINT = "/api/gateway/config/rule/sync";

    private static final String RULE_SYNC_BATCH_ENDPOINT = "/api/gateway/config/rule/sync/batch";

    private static final String RULE_DELETE_ENDPOINT = "/api/gateway/config/rule/";

    private static final String RULE_LIST_ENDPOINT = "/api/gateway/config/rule/list";

    @Value("${cross-service.auth.gateway-token:SecureToken123}")
    private String gatewayAuthToken;

    @Value("${cross-service.auth.monitor-ip:127.0.0.1}")
    private String monitorIp;

    private static final int MAX_RETRY = 3;

    private static final long RETRY_INTERVAL = 1000;

    /**
     * 推送单个规则到网关
     *
     * @param ruleDTO 规则同步DTO
     * @return 是否成功
     */
    @Retryable(
        value = Exception.class,
        maxAttempts = MAX_RETRY,
        backoff = @Backoff(delay = RETRY_INTERVAL)
    )
    public boolean pushRuleToGateway(RuleSyncDTO ruleDTO) {
        try {
            String url = GATEWAY_BASE_URL + RULE_SYNC_ENDPOINT;

            HttpHeaders headers = createHeaders();

            HttpEntity<RuleSyncDTO> request = new HttpEntity<>(ruleDTO, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("推送规则到网关成功: id={}, name={}", ruleDTO.getId(), ruleDTO.getRuleName());
                return true;
            } else {
                log.error("推送规则到网关失败: id={}, statusCode={}", ruleDTO.getId(), response.getStatusCode());
                throw new RuntimeException("推送规则失败，状态码：" + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("推送规则到网关异常: id={}", ruleDTO.getId(), e);
            throw e;
        }
    }

    /**
     * 推送单个规则到网关（不重试版本）
     *
     * @param ruleDTO 规则同步DTO
     * @return 是否成功
     */
    public boolean pushRuleToGatewayNoRetry(RuleSyncDTO ruleDTO) {
        try {
            String url = GATEWAY_BASE_URL + RULE_SYNC_ENDPOINT;

            HttpHeaders headers = createHeaders();

            HttpEntity<RuleSyncDTO> request = new HttpEntity<>(ruleDTO, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("推送规则到网关成功: id={}, name={}", ruleDTO.getId(), ruleDTO.getRuleName());
                return true;
            } else {
                log.error("推送规则到网关失败: id={}, statusCode={}", ruleDTO.getId(), response.getStatusCode());
                return false;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("推送规则到网关失败，网关服务未就绪: id={}, error={}", ruleDTO.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("推送规则到网关异常: id={}", ruleDTO.getId(), e);
            return false;
        }
    }

    /**
     * 批量推送规则到网关
     *
     * @param rules 规则列表
     * @return 是否成功
     */
    public boolean pushRulesToGateway(List<RuleSyncDTO> rules) {
        try {
            String url = GATEWAY_BASE_URL + RULE_SYNC_BATCH_ENDPOINT;

            HttpHeaders headers = createHeaders();

            HttpEntity<List<RuleSyncDTO>> request = new HttpEntity<>(rules, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("批量推送规则到网关成功: count={}", rules.size());
                return true;
            } else {
                log.error("批量推送规则到网关失败: statusCode={}", response.getStatusCode());
                return false;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("批量推送规则到网关失败，网关服务未就绪: error={}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("批量推送规则到网关异常", e);
            return false;
        }
    }

    /**
     * 通知网关删除规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    public boolean deleteRuleFromGateway(Long ruleId) {
        try {
            String url = GATEWAY_BASE_URL + RULE_DELETE_ENDPOINT + ruleId;

            HttpHeaders headers = createHeaders();

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("通知网关删除规则成功: id={}", ruleId);
                return true;
            } else {
                log.error("通知网关删除规则失败: id={}, statusCode={}", ruleId, response.getStatusCode());
                return false;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("通知网关删除规则失败，网关服务未就绪: id={}, error={}", ruleId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("通知网关删除规则异常: id={}", ruleId, e);
            return false;
        }
    }

    /**
     * 从网关获取当前规则列表
     *
     * @return 规则列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGatewayRules() {
        try {
            String url = GATEWAY_BASE_URL + RULE_LIST_ENDPOINT;

            HttpHeaders headers = createHeaders();

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Boolean success = (Boolean) body.get("success");
                if (success != null && success) {
                    log.info("获取网关规则列表成功");
                    return (List<Map<String, Object>>) body.get("data");
                }
            }
            log.error("获取网关规则列表失败: statusCode={}", response.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.error("获取网关规则列表异常", e);
            return List.of();
        }
    }

    /**
     * 创建请求头
     *
     * @return HttpHeaders
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Token", gatewayAuthToken);
        headers.set("X-Source-IP", monitorIp);
        return headers;
    }

    /**
     * 获取客户端统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format("规则同步客户端 - 网关地址: %s", GATEWAY_BASE_URL);
    }
}
