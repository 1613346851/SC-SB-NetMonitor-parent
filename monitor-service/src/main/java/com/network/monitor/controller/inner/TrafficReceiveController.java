package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流量数据接收控制器（对内跨服务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/inner/traffic")
public class TrafficReceiveController {

    @Value("${cross-service.auth.verify-token:MonitorSecureToken456}")
    private String expectedToken;

    @Autowired
    private TrafficAnalyzeService trafficAnalyzeService;

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private DDoSDetectService ddosDetectService;

    @Autowired
    private VulnerabilityVerifyService vulnerabilityVerifyService;

    @Autowired
    private VulnerabilityStatService vulnerabilityStatService;

    @Autowired
    private AttackDetectService attackDetectService;

    @Autowired
    private DefenseDecisionService defenseDecisionService;

    @Autowired
    private TrafficStoreService trafficStoreService;

    @Autowired
    private AttackStoreService attackStoreService;

    @Autowired
    private AttackDetectService attackDetectServiceImpl;

    /**
     * 接收网关推送的流量数据（同步处理）
     * 添加跨服务鉴权验证，确保数据来源合法
     */
    @PostMapping("/receive")
    public ApiResponse<Void> receiveTraffic(
            @RequestBody TrafficMonitorDTO trafficDTO,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken) {
        try {
            // 验证跨服务鉴权 Token
            if (!validateCrossServiceToken(authToken)) {
                log.warn("接收到未授权的流量推送请求：token={}, sourceIp={}", 
                    authToken, trafficDTO.getSourceIp());
                return ApiResponse.error("未授权访问");
            }

            log.info("接收到流量数据：sourceIp={}, uri={}, method={}", 
                trafficDTO.getSourceIp(), trafficDTO.getRequestUri(), trafficDTO.getHttpMethod());

            // 同步处理流量数据（确保数据写入数据库）
            processTrafficSync(trafficDTO);

            log.info("流量数据处理完成：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("接收流量数据失败：sourceIp={}, error={}", 
                trafficDTO.getSourceIp(), e.getMessage(), e);
            return ApiResponse.error("接收流量数据失败：" + e.getMessage());
        }
    }

    /**
     * 验证跨服务鉴权 Token
     */
    private boolean validateCrossServiceToken(String authToken) {
        return expectedToken != null && expectedToken.equals(authToken);
    }

    /**
     * 同步处理流量数据（确保数据写入数据库）
     */
    public void processTrafficSync(TrafficMonitorDTO trafficDTO) {
        try {
            log.debug("开始预处理流量：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            
            // 1. 预处理流量（解码等）
            trafficAnalyzeService.preprocessTraffic(trafficDTO);

            log.debug("开始保存流量数据：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            
            // 2. 保存原始流量数据到数据库
            TrafficMonitorEntity trafficEntity = trafficStoreService.convertToEntity(trafficDTO);
            Long trafficId = trafficStoreService.saveTraffic(trafficEntity);
            
            if (trafficId == null) {
                throw new RuntimeException("保存流量数据失败：sourceIp=" + trafficDTO.getSourceIp());
            }
            
            log.info("流量数据已保存：trafficId={}, sourceIp={}, uri={}", 
                trafficId, trafficDTO.getSourceIp(), trafficEntity.getSourceIp());

            // 3. 执行规则引擎检测
            List<AttackMonitorDTO> detectedAttacks = ruleEngineService.executeMatching(trafficDTO);

            // 4. 执行 DDoS 检测
            AttackMonitorDTO ddosAttack = ddosDetectService.detect(trafficDTO);
            if (ddosAttack != null) {
                detectedAttacks.add(ddosAttack);
            }

            // 5. 处理检测到的攻击
            if (!detectedAttacks.isEmpty()) {
                for (AttackMonitorDTO attack : detectedAttacks) {
                    // 设置关联的流量 ID
                    attack.setTrafficId(trafficId);
                    
                    // 6. 漏洞验证（如果命中预设漏洞）
                    VulnerabilityMonitorEntity matchedVuln = vulnerabilityVerifyService.verifyAttack(attack);
                    
                    // 7. 保存攻击记录到数据库
                    AttackMonitorEntity attackEntity = attackStoreService.convertToEntity(attack);
                    Long attackId = attackStoreService.saveAttack(attackEntity);
                    
                    // 8. 更新漏洞统计
                    if (attackId != null && matchedVuln != null) {
                        // 漏洞验证后更新统计
                        vulnerabilityStatService.incrementAttackCount(
                            matchedVuln.getId(), // 使用命中的漏洞 ID
                            attackId
                        );
                    }
                    
                    // 9. 生成防御决策（高风险攻击）
                    defenseDecisionService.generateDefenseDecision(attack);
                }

                log.info("流量检测完成：trafficId={}, 检测到攻击数={}", trafficId, detectedAttacks.size());
            }
            
        } catch (Exception e) {
            log.error("同步处理流量数据失败：sourceIp={}, error={}", 
                trafficDTO.getSourceIp(), e.getMessage(), e);
            throw new RuntimeException("处理流量数据失败", e);
        }
    }
}
