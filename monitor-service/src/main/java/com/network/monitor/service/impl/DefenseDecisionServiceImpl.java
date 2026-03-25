package com.network.monitor.service.impl;

import com.network.monitor.cache.IpAttackStateCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.common.constant.DefenseTypeConstant;
import com.network.monitor.common.constant.IpAttackStateConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.event.BlacklistSyncEvent;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class DefenseDecisionServiceImpl implements DefenseDecisionService {

    private static final int DEFAULT_RATE_LIMIT_THRESHOLD = 5;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private AttackEventService attackEventService;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO) {
        if (attackDTO == null) {
            return null;
        }

        try {
            String sourceIp = attackDTO.getSourceIp();

            if (attackStateCache.isInDefendedState(sourceIp)) {
                log.debug("IP已处于DEFENDED状态，跳过防御决策: ip={}", sourceIp);
                return null;
            }

            String riskLevelStr = attackDTO.getRiskLevel();
            DefenseCommandDTO commandDTO = null;

            DefenseCommandDTO.RiskLevel riskLevel = parseRiskLevel(riskLevelStr);

            AttackEventEntity event = attackEventService.getOrCreateEvent(
                sourceIp, 
                attackDTO.getAttackType(), 
                riskLevelStr, 
                attackDTO.getConfidence() != null ? attackDTO.getConfidence() : 80
            );

            String eventId = event != null ? event.getEventId() : generateEventId();

            if (riskLevel == DefenseCommandDTO.RiskLevel.HIGH || riskLevel == DefenseCommandDTO.RiskLevel.CRITICAL) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.BLACKLIST,
                        sourceIp,
                        "检测到高风险攻击：" + attackDTO.getAttackType(),
                        calculateExpireTime(30),
                        eventId
                );
            } else if (riskLevel == DefenseCommandDTO.RiskLevel.MEDIUM) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.RATE_LIMIT,
                        sourceIp,
                        "检测到中风险攻击：" + attackDTO.getAttackType(),
                        calculateExpireTime(60),
                        eventId
                );
                if (commandDTO != null) {
                    commandDTO.setRateLimitThreshold(DEFAULT_RATE_LIMIT_THRESHOLD);
                }
            }

            if (commandDTO != null) {
                boolean success = gatewayApiClient.pushDefenseCommand(commandDTO);
                if (success) {
                    recordDefenseLog(commandDTO, event, attackDTO);
                    
                    if (commandDTO.getDefenseType() == DefenseCommandDTO.DefenseType.BLACKLIST) {
                        publishBlacklistSyncEvent(commandDTO);
                    }
                    
                    attackStateCache.markAsDefended(sourceIp, eventId);
                    
                    if (event != null) {
                        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(
                            commandDTO.getDefenseType() == DefenseCommandDTO.DefenseType.BLACKLIST ? 30 : 60
                        );
                        attackEventService.setDefenseInfo(
                            event.getId(), 
                            commandDTO.getDefenseType().name(), 
                            expireTime, 
                            true
                        );
                    }
                    
                    log.info("生成并推送防御决策成功：attackId={}, defenseType={}, sourceIp={}, eventId={}",
                            attackDTO.getTrafficId(), commandDTO.getDefenseType(), sourceIp, eventId);
                } else {
                    log.error("推送防御指令失败：attackId={}", attackDTO.getTrafficId());
                }
            }

            return commandDTO;
        } catch (Exception e) {
            log.error("生成防御决策失败：", e);
            return null;
        }
    }

    @Override
    public DefenseCommandDTO generateManualDefense(String defenseType, String target, String reason) {
        try {
            String eventId = generateEventId();

            DefenseCommandDTO commandDTO = new DefenseCommandDTO();
            commandDTO.setSourceIp(target);
            commandDTO.setDefenseType(parseDefenseType(defenseType));
            commandDTO.setRiskLevel(DefenseCommandDTO.RiskLevel.HIGH);
            commandDTO.setDescription(reason);
            commandDTO.setEventId(eventId);

            if (commandDTO.getDefenseType() == DefenseCommandDTO.DefenseType.RATE_LIMIT) {
                commandDTO.setRateLimitThreshold(DEFAULT_RATE_LIMIT_THRESHOLD);
            }

            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(120);
            commandDTO.setExpireTimestamp(
                    expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            );

            gatewayApiClient.pushDefenseCommand(commandDTO);

            attackStateCache.markAsDefended(target, eventId);

            log.info("生成手动防御决策：type={}, target={}, reason={}, eventId={}", defenseType, target, reason, eventId);
            return commandDTO;
        } catch (Exception e) {
            log.error("生成手动防御决策失败：", e);
            return null;
        }
    }

    private String generateEventId() {
        return "EVT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private DefenseCommandDTO buildDefenseCommand(AttackMonitorDTO attackDTO,
                                                  DefenseCommandDTO.DefenseType defenseType,
                                                  String target,
                                                  String reason,
                                                  String expireTime,
                                                  String eventId) {
        DefenseCommandDTO dto = new DefenseCommandDTO();
        dto.setSourceIp(target);
        dto.setDefenseType(defenseType);
        dto.setRiskLevel(parseRiskLevel(attackDTO.getRiskLevel()));
        dto.setDescription(reason);
        dto.setEventId(eventId);

        if (expireTime != null) {
            try {
                LocalDateTime exp = LocalDateTime.parse(expireTime, TIME_FORMATTER);
                dto.setExpireTimestamp(
                        exp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                );
            } catch (Exception e) {
                log.warn("解析过期时间失败：{}", expireTime);
            }
        }

        return dto;
    }

    private DefenseCommandDTO.DefenseType parseDefenseType(String defenseType) {
        if (defenseType == null || defenseType.isBlank()) {
            return DefenseCommandDTO.DefenseType.BLACKLIST;
        }
        return switch (defenseType.toUpperCase()) {
            case "BLACKLIST", "BLOCK_IP", "IP_BLOCK" -> DefenseCommandDTO.DefenseType.BLACKLIST;
            case "RATE_LIMIT" -> DefenseCommandDTO.DefenseType.RATE_LIMIT;
            case "BLOCK", "BLOCK_REQUEST", "MALICIOUS_REQUEST" -> DefenseCommandDTO.DefenseType.BLOCK;
            default -> DefenseCommandDTO.DefenseType.BLACKLIST;
        };
    }

    private DefenseCommandDTO.RiskLevel parseRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return DefenseCommandDTO.RiskLevel.LOW;
        }
        return switch (riskLevel.toUpperCase()) {
            case "CRITICAL" -> DefenseCommandDTO.RiskLevel.CRITICAL;
            case "HIGH" -> DefenseCommandDTO.RiskLevel.HIGH;
            case "MEDIUM" -> DefenseCommandDTO.RiskLevel.MEDIUM;
            case "LOW" -> DefenseCommandDTO.RiskLevel.LOW;
            default -> DefenseCommandDTO.RiskLevel.LOW;
        };
    }

    private String calculateExpireTime(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes).format(TIME_FORMATTER);
    }

    private void recordDefenseLog(DefenseCommandDTO commandDTO, AttackEventEntity event, AttackMonitorDTO attackDTO) {
        try {
            DefenseLogEntity logEntity = new DefenseLogEntity();
            logEntity.setEventId(commandDTO.getEventId());
            logEntity.setDefenseType(convertDefenseType(commandDTO.getDefenseType()));
            logEntity.setDefenseTarget(commandDTO.getSourceIp());
            logEntity.setDefenseReason(commandDTO.getDescription());
            logEntity.setDefenseAction("ADD_BLACKLIST");
            logEntity.setExecuteStatus(1);
            logEntity.setIsFirst(1);
            logEntity.setOperator("SYSTEM");
            
            if (event != null) {
                logEntity.setAttackId(event.getId());
            }
            
            if (attackDTO != null) {
                logEntity.setTrafficId(attackDTO.getTrafficId());
            }
            
            if (commandDTO.getExpireTimestamp() != null) {
                logEntity.setExpireTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(commandDTO.getExpireTimestamp()),
                    ZoneId.systemDefault()
                ));
            }
            
            LocalDateTime now = LocalDateTime.now();
            logEntity.setExecuteTime(now);
            logEntity.setCreateTime(now);
            logEntity.setUpdateTime(now);
            
            defenseLogMapper.insert(logEntity);
            
            log.info("记录防御日志成功：eventId={}, defenseType={}, target={}, isFirst=1", 
                commandDTO.getEventId(), logEntity.getDefenseType(), logEntity.getDefenseTarget());
        } catch (Exception e) {
            log.error("记录防御日志失败：eventId={}, target={}", 
                commandDTO.getEventId(), commandDTO.getSourceIp(), e);
        }
    }

    private void publishBlacklistSyncEvent(DefenseCommandDTO commandDTO) {
        try {
            LocalDateTime expireTime = null;
            if (commandDTO.getExpireTimestamp() != null) {
                expireTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(commandDTO.getExpireTimestamp()),
                    ZoneId.systemDefault()
                );
            }
            
            BlacklistSyncEvent syncEvent = BlacklistSyncEvent.add(
                this,
                commandDTO.getSourceIp(),
                commandDTO.getDescription(),
                expireTime,
                "SYSTEM"
            );
            
            eventPublisher.publishEvent(syncEvent);
            
            log.info("发布黑名单同步事件：ip={}, reason={}, expireTime={}", 
                commandDTO.getSourceIp(), commandDTO.getDescription(), expireTime);
        } catch (Exception e) {
            log.error("发布黑名单同步事件失败：ip={}", commandDTO.getSourceIp(), e);
        }
    }

    private String convertDefenseType(DefenseCommandDTO.DefenseType defenseType) {
        if (defenseType == null) {
            return DefenseTypeConstant.BLOCK_IP;
        }
        return switch (defenseType) {
            case BLACKLIST -> DefenseTypeConstant.BLOCK_IP;
            case RATE_LIMIT -> DefenseTypeConstant.RATE_LIMIT;
            case BLOCK -> DefenseTypeConstant.BLOCK_REQUEST;
        };
    }
}
