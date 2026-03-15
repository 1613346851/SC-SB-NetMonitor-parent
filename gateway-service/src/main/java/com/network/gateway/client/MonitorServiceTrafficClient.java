package com.network.gateway.client;

import com.network.gateway.constant.GatewayHttpConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
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

/**
 * 监控服务流量推送客户端
 * 负责将流量数据异步推送到监控服务
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class MonitorServiceTrafficClient {

    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceTrafficClient.class);

    @Autowired
    @Qualifier("restTemplate")
    private RestTemplate restTemplate;

    /**
     * 跨服务安全密钥（从配置文件读取，需与监测服务一致）
     */
    @Value("${cross-service.security.secret-key:DefaultSecretKeyPleaseChangeInProduction123456}")
    private String secretKey;

    /**
     * 推送流量数据到监控服务
     * 使用时间戳+签名进行安全验证
     *
     * @param trafficDTO 流量监控 DTO
     * @throws RestClientException 网络异常
     */
    public void pushTraffic(TrafficMonitorDTO trafficDTO) throws RestClientException {
        if (trafficDTO == null) {
            logger.warn("尝试推送空的流量数据");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> securityHeaders = CrossServiceSecurityUtil.generateSecurityHeaders(
                trafficDTO.getRequestId(), secretKey
            );
            securityHeaders.forEach(headers::set);

            headers.set("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()));

            HttpEntity<TrafficMonitorDTO> requestEntity = new HttpEntity<>(trafficDTO, headers);

            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.TRAFFIC_MONITOR_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("流量数据推送成功: 请求ID[{}] 状态码[{}]", 
                           trafficDTO.getRequestId(), response.getStatusCode());
            } else {
                logger.warn("流量数据推送返回非成功状态: 请求ID[{}] 状态码[{}] 响应内容[{}]", 
                           trafficDTO.getRequestId(), response.getStatusCode(), response.getBody());
                throw new RestClientException("监控服务返回错误状态: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            logger.error("推送流量数据到监控服务失败: 请求ID[{}] 错误: {}", 
                        trafficDTO.getRequestId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送流量数据时发生未知异常: 请求ID[{}]", trafficDTO.getRequestId(), e);
            throw new RestClientException("推送流量数据时发生未知异常", e);
        }
    }

    /**
     * 批量推送流量数据（简化版）
     *
     * @param trafficDTOs 流量监控DTO集合
     * @return 成功推送的数量
     */
    public int batchPushTraffic(java.util.List<TrafficMonitorDTO> trafficDTOs) {
        if (trafficDTOs == null || trafficDTOs.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (TrafficMonitorDTO trafficDTO : trafficDTOs) {
            try {
                pushTraffic(trafficDTO);
                successCount++;
            } catch (Exception e) {
                logger.warn("批量推送中单个流量数据失败: 请求ID[{}]", trafficDTO.getRequestId(), e);
            }
        }

        logger.info("批量推送流量数据完成: 总数{} 成功{} 失败{}", 
                   trafficDTOs.size(), successCount, trafficDTOs.size() - successCount);

        return successCount;
    }

    /**
     * 异步推送流量数据
     *
     * @param trafficDTO 流量监控DTO
     */
    public void pushTrafficAsync(TrafficMonitorDTO trafficDTO) {
        // 使用异步方式推送
        new Thread(() -> {
            try {
                pushTraffic(trafficDTO);
            } catch (Exception e) {
                logger.warn("异步推送流量数据失败: 请求ID[{}]", trafficDTO.getRequestId(), e);
            }
        }, "traffic-push-thread-" + trafficDTO.getRequestId()).start();
    }

    /**
     * 带重试机制的流量推送
     *
     * @param trafficDTO 流量监控DTO
     * @param maxRetries 最大重试次数
     * @return true表示推送成功
     */
    public boolean pushTrafficWithRetry(TrafficMonitorDTO trafficDTO, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                pushTraffic(trafficDTO);
                return true;
            } catch (Exception e) {
                logger.warn("第{}次推送流量数据失败: 请求ID[{}], 错误: {}", 
                           i + 1, trafficDTO.getRequestId(), e.getMessage());
                
                if (i < maxRetries) {
                    // 等待后重试
                    try {
                        Thread.sleep(1000 * (i + 1)); // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        logger.error("流量数据推送最终失败，已达到最大重试次数: 请求ID[{}]", trafficDTO.getRequestId());
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
    
            String url = GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.TRAFFIC_MONITOR_ENDPOINT;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    entity,
                    String.class
            );
    
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.debug("监控服务连通性检查失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取客户端统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        // 这里可以添加更详细的统计信息收集
        return "流量推送客户端 - 配置端点：" + GatewayHttpConstant.MonitorService.BASE_URL + GatewayHttpConstant.MonitorService.TRAFFIC_MONITOR_ENDPOINT;
    }
}