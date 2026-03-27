package com.network.monitor.controller.inner;

import com.network.monitor.cache.IpAttackStateCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficAggregateDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inner/traffic")
public class TrafficReceiveController {

    @Autowired
    private TrafficAnalyzeService trafficAnalyzeService;

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private VulnerabilityVerifyService vulnerabilityVerifyService;

    @Autowired
    private VulnerabilityStatService vulnerabilityStatService;

    @Autowired
    private DefenseDecisionService defenseDecisionService;

    @Autowired
    private TrafficStoreService trafficStoreService;

    @Autowired
    private AttackStoreService attackStoreService;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @PostMapping("/receive")
    public ApiResponse<Void> receiveTraffic(@RequestBody TrafficMonitorDTO trafficDTO) {
        try {
            if (trafficDTO.isAggregated()) {
                log.info("接收到聚合流量数据：sourceIp={}, uri={}, method={}, requestCount={}, stateTag={}", 
                    trafficDTO.getSourceIp(), trafficDTO.getRequestUri(), trafficDTO.getHttpMethod(),
                    trafficDTO.getRequestCount(), trafficDTO.getStateTag());
            } else {
                log.info("接收到流量数据：sourceIp={}, uri={}, method={}", 
                    trafficDTO.getSourceIp(), trafficDTO.getRequestUri(), trafficDTO.getHttpMethod());
            }

            if (trafficDTO.isSkipPush()) {
                log.debug("流量标记为跳过推送，跳过处理: sourceIp={}", trafficDTO.getSourceIp());
                return ApiResponse.success();
            }

            processTrafficSync(trafficDTO);

            if (trafficDTO.isAggregated()) {
                log.info("聚合流量数据处理完成：sourceIp={}, uri={}, requestCount={}", 
                    trafficDTO.getSourceIp(), trafficDTO.getRequestUri(), trafficDTO.getRequestCount());
            } else {
                log.info("流量数据处理完成：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            }
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("接收流量数据失败：sourceIp={}, error={}", 
                trafficDTO.getSourceIp(), e.getMessage(), e);
            return ApiResponse.error("接收流量数据失败：" + e.getMessage());
        }
    }

    public void processTrafficSync(TrafficMonitorDTO trafficDTO) {
        try {
            log.debug("开始预处理流量：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            
            trafficAnalyzeService.preprocessTraffic(trafficDTO);

            log.debug("开始保存流量数据：sourceIp={}, uri={}", trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
            
            TrafficMonitorEntity trafficEntity = trafficStoreService.convertToEntity(trafficDTO);
            Long trafficId = trafficStoreService.saveTraffic(trafficEntity);
            
            if (trafficId == null) {
                throw new RuntimeException("保存流量数据失败：sourceIp=" + trafficDTO.getSourceIp());
            }
            
            if (trafficDTO.isAggregated()) {
                log.info("聚合流量数据已保存：trafficId={}, sourceIp={}, uri={}, requestCount={}", 
                    trafficId, trafficDTO.getSourceIp(), trafficEntity.getRequestUri(), trafficDTO.getRequestCount());
                return;
            }
            
            log.info("流量数据已保存：trafficId={}, sourceIp={}, uri={}", 
                trafficId, trafficDTO.getSourceIp(), trafficEntity.getSourceIp());

            List<AttackMonitorDTO> detectedAttacks = ruleEngineService.executeMatching(trafficDTO);
            if (detectedAttacks == null) {
                detectedAttacks = new ArrayList<>();
            }

            if (!detectedAttacks.isEmpty()) {
                for (AttackMonitorDTO attack : detectedAttacks) {
                    attack.setTrafficId(trafficId);
                    
                    VulnerabilityMonitorEntity matchedVuln = vulnerabilityVerifyService.verifyAttack(attack);
                    
                    AttackMonitorEntity attackEntity = attackStoreService.convertToEntity(attack);
                    Long attackId = attackStoreService.saveAttack(attackEntity);
                    
                    if (attackId != null && matchedVuln != null) {
                        vulnerabilityStatService.incrementAttackCount(
                            matchedVuln.getId(),
                            attackId
                        );
                    }
                    
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

    @PostMapping("/aggregate")
    public ApiResponse<Void> receiveAggregateTraffic(@RequestBody TrafficAggregateDTO aggregateDTO) {
        try {
            log.info("接收到聚合流量数据: ip={}, state={}, totalRequests={}, uriGroups={}, samples={}", 
                aggregateDTO.getIp(), 
                aggregateDTO.getStateName(),
                aggregateDTO.getTotalRequests(),
                aggregateDTO.getUriGroupCount(),
                aggregateDTO.getSampleCount());

            if (aggregateDTO.hasTransition()) {
                log.info("检测到状态转换: ip={}, {} -> {}, reason={}", 
                    aggregateDTO.getIp(),
                    aggregateDTO.getTransition().getFromState(),
                    aggregateDTO.getTransition().getToState(),
                    aggregateDTO.getTransition().getReason());
            }

            List<TrafficAggregateDTO.UriGroupStatsDTO> uriGroups = aggregateDTO.getUriGroups();
            if (uriGroups != null && !uriGroups.isEmpty()) {
                for (TrafficAggregateDTO.UriGroupStatsDTO uriGroup : uriGroups) {
                    TrafficMonitorDTO trafficDTO = convertUriGroupToTrafficDTO(aggregateDTO, uriGroup);
                    
                    trafficAnalyzeService.preprocessTraffic(trafficDTO);
                    
                    TrafficMonitorEntity trafficEntity = trafficStoreService.convertToEntity(trafficDTO);
                    Long trafficId = trafficStoreService.saveTraffic(trafficEntity);
                    
                    if (trafficId != null) {
                        log.debug("聚合流量分组已保存: trafficId={}, ip={}, uri={}, count={}", 
                            trafficId, aggregateDTO.getIp(), uriGroup.getUriPattern(), uriGroup.getCount());
                    }
                }
            }

            log.info("聚合流量数据处理完成: ip={}, totalRequests={}, rps={}", 
                aggregateDTO.getIp(), aggregateDTO.getTotalRequests(), aggregateDTO.getRps());
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("接收聚合流量数据失败: ip={}, error={}", 
                aggregateDTO.getIp(), e.getMessage(), e);
            return ApiResponse.error("接收聚合流量数据失败：" + e.getMessage());
        }
    }

    private TrafficMonitorDTO convertUriGroupToTrafficDTO(TrafficAggregateDTO aggregate, TrafficAggregateDTO.UriGroupStatsDTO uriGroup) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        
        dto.setRequestId(aggregate.getRequestId());
        dto.setRequestTime(aggregate.getRequestTime());
        dto.setSourceIp(aggregate.getIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri(uriGroup.getUriPattern());
        dto.setHttpMethod(uriGroup.getHttpMethod());
        dto.setStateTag(aggregate.getStateName());
        dto.setStateValue(aggregate.getState());
        
        if (aggregate.getTransition() != null) {
            dto.setConfidence(aggregate.getTransition().getConfidence());
        } else {
            dto.setConfidence(aggregate.getConfidence());
        }
        
        dto.setRequestCount(uriGroup.getCount());
        dto.setErrorCount(uriGroup.getErrorCount());
        dto.setAvgProcessingTime(uriGroup.getAvgProcessingTime());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(aggregate.getStartTime());
        dto.setAggregateEndTime(aggregate.getEndTime());
        
        dto.setResponseStatus(200);
        dto.setResponseTime(uriGroup.getAvgProcessingTime());
        
        return dto;
    }
}

