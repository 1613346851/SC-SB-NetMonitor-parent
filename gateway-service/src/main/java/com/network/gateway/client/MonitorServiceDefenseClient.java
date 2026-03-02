package com.network.gateway.client;

import com.network.gateway.constant.GatewayHttpConstant;
import com.network.gateway.dto.DefenseLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    /**
     * 推送防御日志到监控服务
     *
     * @param defenseLogDTO 防御日志DTO
     * @throws RestClientException 网络异常
     */
    public void pushDefenseLog(DefenseLogDTO defenseLogDTO) throws RestClientException {
        if (defenseLogDTO == null) {
            logger.warn("尝试推送空的防御日志");
            return;
        }

        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()));
            headers.set("X-Defense-Log-ID", defenseLogDTO.getLogId());
            headers.set("X-Defense-Type", defenseLogDTO.getDefenseType().name());

            // 构建请求实体
            HttpEntity<DefenseLogDTO> requestEntity = new HttpEntity<>(defenseLogDTO, headers);

            // 发送POST请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT,
                    requestEntity,
                    String.class
            );

            // 检查响应状态
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("防御日志推送成功: 日志ID[{}] 防御类型[{}] 状态码[{}]", 
                           defenseLogDTO.getLogId(), 
                           defenseLogDTO.getDefenseType(),
                           response.getStatusCode());
            } else {
                logger.warn("防御日志推送返回非成功状态: 日志ID[{}] 状态码[{}] 响应内容[{}]", 
                           defenseLogDTO.getLogId(), response.getStatusCode(), response.getBody());
                throw new RestClientException("监控服务返回错误状态: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            logger.error("推送防御日志到监控服务失败: 日志ID[{}] 防御类型[{}] 错误: {}", 
                        defenseLogDTO.getLogId(), 
                        defenseLogDTO.getDefenseType(),
                        e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("推送防御日志时发生未知异常: 日志ID[{}] 防御类型[{}]", 
                        defenseLogDTO.getLogId(), 
                        defenseLogDTO.getDefenseType(), e);
            throw new RestClientException("推送防御日志时发生未知异常", e);
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
                logger.warn("批量推送中单个防御日志失败: 日志ID[{}]", defenseLogDTO.getLogId(), e);
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
        // 使用异步方式推送
        new Thread(() -> {
            try {
                pushDefenseLog(defenseLogDTO);
            } catch (Exception e) {
                logger.warn("异步推送防御日志失败: 日志ID[{}]", defenseLogDTO.getLogId(), e);
            }
        }, "defense-log-push-thread-" + defenseLogDTO.getLogId()).start();
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
                logger.warn("第{}次推送防御日志失败: 日志ID[{}], 错误: {}", 
                           i + 1, defenseLogDTO.getLogId(), e.getMessage());
                
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
        
        logger.error("防御日志推送最终失败，已达到最大重试次数: 日志ID[{}]", defenseLogDTO.getLogId());
        return false;
    }

    /**
     * 推送高风险防御日志（紧急处理）
     *
     * @param defenseLogDTO 防御日志DTO
     * @return true表示推送成功
     */
    public boolean pushHighRiskDefenseLog(DefenseLogDTO defenseLogDTO) {
        try {
            // 为高风险日志添加特殊标记
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()));
            headers.set("X-Defense-Log-ID", defenseLogDTO.getLogId());
            headers.set("X-Defense-Type", defenseLogDTO.getDefenseType().name());
            headers.set("X-Risk-Level", "HIGH"); // 高风险标记
            headers.set("X-Priority", "URGENT"); // 紧急优先级

            HttpEntity<DefenseLogDTO> requestEntity = new HttpEntity<>(defenseLogDTO, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT,
                    requestEntity,
                    String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                logger.info("高风险防御日志推送成功: 日志ID[{}] 防御类型[{}]", 
                           defenseLogDTO.getLogId(), defenseLogDTO.getDefenseType());
            } else {
                logger.error("高风险防御日志推送失败: 日志ID[{}] 状态码[{}]", 
                            defenseLogDTO.getLogId(), response.getStatusCode());
            }
            
            return success;
        } catch (Exception e) {
            logger.error("推送高风险防御日志时发生异常: 日志ID[{}]", defenseLogDTO.getLogId(), e);
            return false;
        }
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

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT,
                    entity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.debug("监控服务防御日志接口连通性检查失败: {}", e.getMessage());
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
        return "防御日志推送客户端 - 配置端点: " + GatewayHttpConstant.MonitorService.DEFENSE_LOG_ENDPOINT;
    }
}