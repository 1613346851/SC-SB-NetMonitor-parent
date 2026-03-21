package com.network.monitor.service.impl;

import com.network.monitor.cache.IpAttackStateCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.common.constant.IpAttackStateConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

            if (attackStateCache.hasActiveEvent(sourceIp)) {
                log.debug("IP已有活跃攻击事件，跳过防御决策: ip={}, eventId={}", sourceIp, attackStateCache.getEventId(sourceIp));
                return null;
            }

            String riskLevelStr = attackDTO.getRiskLevel();
            DefenseCommandDTO commandDTO = null;

            DefenseCommandDTO.RiskLevel riskLevel = parseRiskLevel(riskLevelStr);

            String eventId = generateEventId();

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
                    attackStateCache.markAsDefended(sourceIp, eventId);
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
}
