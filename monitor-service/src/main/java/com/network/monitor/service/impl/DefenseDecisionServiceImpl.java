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
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.DefenseDecisionService;
import com.network.monitor.service.SysConfigService;
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

    private static final int DEFAULT_RATE_LIMIT_THRESHOLD = 15;

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

    @Autowired
    private SysConfigService sysConfigService;
    
    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int getRateLimitThreshold() {
        try {
            String value = sysConfigService.getConfigValue("defense.decision.rate-limit-threshold");
            if (value != null && !value.isEmpty()) {
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("获取防御决策限流阈值配置失败，使用默认值: {}", DEFAULT_RATE_LIMIT_THRESHOLD);
        }
        return DEFAULT_RATE_LIMIT_THRESHOLD;
    }

    @Override
    public DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO) {
        if (attackDTO == null) {
            return null;
        }

        try {
            String sourceIp = attackDTO.getSourceIp();
            String riskLevelStr = attackDTO.getRiskLevel();
            DefenseCommandDTO commandDTO = null;

            DefenseCommandDTO.RiskLevel riskLevel = parseRiskLevel(riskLevelStr);

            String existingEventId = attackDTO.getEventId();
            AttackEventEntity event = null;
            String eventId;
            
            if (existingEventId != null && !existingEventId.isEmpty()) {
                event = attackEventService.getEventByEventId(existingEventId);
                eventId = existingEventId;
                log.debug("使用已有事件：eventId={}, ip={}", eventId, sourceIp);
            } else {
                event = attackEventService.getOrCreateEvent(
                    sourceIp, 
                    attackDTO.getAttackType(), 
                    riskLevelStr, 
                    attackDTO.getConfidence() != null ? attackDTO.getConfidence() : 80
                );
                eventId = event != null ? event.getEventId() : generateEventId();
                
                if (attackDTO.getAttackId() != null && eventId != null && !eventId.isEmpty()) {
                    try {
                        attackMonitorMapper.updateEventId(attackDTO.getAttackId(), eventId);
                        log.info("更新攻击记录的eventId：attackId={}, eventId={}", attackDTO.getAttackId(), eventId);
                    } catch (Exception e) {
                        log.warn("更新攻击记录eventId失败：attackId={}, eventId={}", attackDTO.getAttackId(), eventId, e);
                    }
                }
            }

            if (attackStateCache.isInDefendedState(sourceIp)) {
                log.debug("IP已处于DEFENDED状态，跳过防御决策: ip={}", sourceIp);
                return null;
            }

            String attackContent = attackDTO.getAttackContent();
            if (attackContent != null && attackContent.contains("[URL路径中的命令关键字，可能是误报或低风险漏洞]")) {
                log.info("检测到可能误报的攻击，跳过防御: ip={}, attackType={}", sourceIp, attackDTO.getAttackType());
                return null;
            }

            String attackType = attackDTO.getAttackType();
            boolean isDdosAttack = "DDOS".equals(attackType) || "BRUTE_FORCE".equals(attackType);

            if (isDdosAttack) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.RATE_LIMIT,
                        sourceIp,
                        "检测到DDoS攻击，启动限流保护",
                        calculateExpireTime(60),
                        eventId
                );
                if (commandDTO != null) {
                    commandDTO.setRateLimitThreshold(getRateLimitThreshold());
                }
            } else if (riskLevel == DefenseCommandDTO.RiskLevel.HIGH || riskLevel == DefenseCommandDTO.RiskLevel.CRITICAL) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.BLOCK,
                        sourceIp,
                        "检测到高风险攻击：" + attackDTO.getAttackType() + "，拦截恶意请求",
                        calculateExpireTime(30),
                        eventId
                );
            } else if (riskLevel == DefenseCommandDTO.RiskLevel.MEDIUM) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.BLOCK,
                        sourceIp,
                        "检测到中风险攻击：" + attackDTO.getAttackType() + "，拦截恶意请求",
                        calculateExpireTime(30),
                        eventId
                );
            } else if (riskLevel == DefenseCommandDTO.RiskLevel.LOW) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.BLOCK,
                        sourceIp,
                        "检测到低风险攻击：" + attackDTO.getAttackType() + "，拦截恶意请求",
                        calculateExpireTime(15),
                        eventId
                );
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
                commandDTO.setRateLimitThreshold(getRateLimitThreshold());
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
            String eventId = commandDTO.getEventId();
            
            if (eventId == null || eventId.isEmpty()) {
                log.info("eventId为空，跳过监控服务生成的防御日志，由网关负责记录：target={}", commandDTO.getSourceIp());
                return;
            }
            
            int existingCount = defenseLogMapper.countByEventId(eventId);
            if (existingCount > 0) {
                log.info("防御日志已存在，跳过重复记录：eventId={}, target={}", eventId, commandDTO.getSourceIp());
                return;
            }
            
            DefenseLogEntity logEntity = new DefenseLogEntity();
            logEntity.setEventId(eventId);
            logEntity.setDefenseType(convertDefenseType(commandDTO.getDefenseType()));
            logEntity.setDefenseTarget(commandDTO.getSourceIp());
            logEntity.setDefenseReason(commandDTO.getDescription());
            logEntity.setDefenseAction(convertDefenseAction(commandDTO.getDefenseType()));
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
                eventId, logEntity.getDefenseType(), logEntity.getDefenseTarget());
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

    private String convertDefenseAction(DefenseCommandDTO.DefenseType defenseType) {
        if (defenseType == null) {
            return "ADD_BLACKLIST";
        }
        return switch (defenseType) {
            case BLACKLIST -> "ADD_BLACKLIST";
            case RATE_LIMIT -> "ADD_RATE_LIMIT";
            case BLOCK -> "ADD_BLOCK";
        };
    }
}
