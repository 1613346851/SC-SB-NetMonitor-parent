package com.network.monitor.client;

import com.network.monitor.common.constant.HttpConstant;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.dto.DefenseLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 网关服务 API 调用客户端
 * 封装与网关服务的所有跨服务调用逻辑，支持失败重试机制
 */
@Slf4j
@Component
public class GatewayApiClient {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 网关服务基础 URL（可配置）
     */
    private static final String GATEWAY_BASE_URL = "http://localhost:9000";

    /**
     * 跨服务鉴权 Token（从配置文件读取）
     */
    @Value("${cross-service.auth.gateway-token:SecureToken123}")
    private String gatewayAuthToken;

    /**
     * 监测服务 IP 地址（用于跨服务鉴权）
     */
    @Value("${cross-service.auth.monitor-ip:127.0.0.1}")
    private String monitorIp;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL = 1000;

    /**
     * 推送防御指令到网关（支持重试机制）
     * 添加跨服务鉴权头，确保调用安全性
     *
     * @param commandDTO 防御指令
     * @return 是否成功
     */
    @Retryable(
        value = Exception.class,
        maxAttempts = MAX_RETRY,
        backoff = @Backoff(delay = RETRY_INTERVAL)
    )
    public boolean pushDefenseCommand(DefenseCommandDTO commandDTO) {
        try {
            String url = GATEWAY_BASE_URL + HttpConstant.GATEWAY_DEFENSE_ENDPOINT;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 添加跨服务鉴权头
            headers.set("X-Auth-Token", gatewayAuthToken);
            headers.set("X-Source-IP", monitorIp);
            
            HttpEntity<DefenseCommandDTO> request = new HttpEntity<>(commandDTO, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("推送防御指令到网关成功：defenseType={}, sourceIp={}", 
                    commandDTO.getDefenseType(), commandDTO.getSourceIp());
                return true;
            } else {
                log.error("推送防御指令到网关失败：statusCode={}", response.getStatusCode());
                throw new RuntimeException("推送防御指令失败，状态码：" + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("推送防御指令到网关异常，剩余重试次数：", e);
            throw e; // 抛出异常以触发重试
        }
    }

    /**
     * 推送防御指令到网关（不重试版本，用于手动调用）
     *
     * @param commandDTO 防御指令
     * @return 是否成功
     */
    public boolean pushDefenseCommandNoRetry(DefenseCommandDTO commandDTO) {
        try {
            String url = GATEWAY_BASE_URL + HttpConstant.GATEWAY_DEFENSE_ENDPOINT;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DefenseCommandDTO> request = new HttpEntity<>(commandDTO, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("推送防御指令到网关成功：defenseType={}, sourceIp={}", 
                    commandDTO.getDefenseType(), commandDTO.getSourceIp());
                return true;
            } else {
                log.error("推送防御指令到网关失败：statusCode={}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("推送防御指令到网关异常（不重试）：", e);
            return false;
        }
    }

    /**
     * 同步防御日志到网关
     *
     * @param logDTO 防御日志
     * @return 是否成功
     */
    public boolean syncDefenseLog(DefenseLogDTO logDTO) {
        try {
            String url = GATEWAY_BASE_URL + HttpConstant.GATEWAY_LOG_ENDPOINT;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DefenseLogDTO> request = new HttpEntity<>(logDTO, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("同步防御日志到网关成功：attackId={}, executeStatus={}", 
                    logDTO.getAttackId(), logDTO.getExecuteStatus());
                return true;
            } else {
                log.error("同步防御日志到网关失败：statusCode={}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("同步防御日志到网关异常：", e);
            return false;
        }
    }
}
