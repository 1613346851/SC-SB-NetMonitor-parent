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
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流量数据接收控制器（对内跨服务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/inner/traffic")
public class TrafficReceiveController {

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
     * 接收网关推送的流量数据（异步处理）
     */
    @PostMapping("/receive")
    public ApiResponse<Void> receiveTraffic(@RequestBody TrafficMonitorDTO trafficDTO) {
        try {
            log.info("接收到流量数据：sourceIp={}, uri={}", 
                trafficDTO.getSourceIp(), trafficDTO.getRequestUri());

            // 异步处理流量数据
            processTrafficAsync(trafficDTO);

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("接收流量数据失败：", e);
            return ApiResponse.error("接收流量数据失败");
        }
    }

    /**
     * 异步处理流量数据
     */
    @Async
    public void processTrafficAsync(TrafficMonitorDTO trafficDTO) {
        try {
            // 1. 预处理流量（解码等）
            trafficAnalyzeService.preprocessTraffic(trafficDTO);

            // 2. 保存原始流量数据到数据库
            TrafficMonitorEntity trafficEntity = trafficStoreService.convertToEntity(trafficDTO);
            Long trafficId = trafficStoreService.saveTraffic(trafficEntity);

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
            log.error("异步处理流量数据失败：", e);
        }
    }
}
