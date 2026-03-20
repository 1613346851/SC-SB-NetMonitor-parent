package com.network.monitor.service.impl;

import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 防御决策生成服务实现类
 */
@Slf4j
@Service
public class DefenseDecisionServiceImpl implements DefenseDecisionService {

    private static final int DEFAULT_RATE_LIMIT_THRESHOLD = 5;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO) {
        if (attackDTO == null) {
            return null;
        }

        try {
            String riskLevelStr = attackDTO.getRiskLevel();
            DefenseCommandDTO commandDTO = null;

            DefenseCommandDTO.RiskLevel riskLevel = parseRiskLevel(riskLevelStr);

            if (riskLevel == DefenseCommandDTO.RiskLevel.HIGH || riskLevel == DefenseCommandDTO.RiskLevel.CRITICAL) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.BLACKLIST,
                        attackDTO.getSourceIp(),
                        "检测到高风险攻击：" + attackDTO.getAttackType(),
                        calculateExpireTime(30)
                );
            } else if (riskLevel == DefenseCommandDTO.RiskLevel.MEDIUM) {
                commandDTO = buildDefenseCommand(
                        attackDTO,
                        DefenseCommandDTO.DefenseType.RATE_LIMIT,
                        attackDTO.getSourceIp(),
                        "检测到中风险攻击：" + attackDTO.getAttackType(),
                        calculateExpireTime(60)
                );
                if (commandDTO != null) {
                    commandDTO.setRateLimitThreshold(DEFAULT_RATE_LIMIT_THRESHOLD);
                }
            }

            if (commandDTO != null) {
                boolean success = gatewayApiClient.pushDefenseCommand(commandDTO);
                if (success) {
                    log.info("生成并推送防御决策成功：attackId={}, defenseType={}, sourceIp={}",
                            attackDTO.getTrafficId(), commandDTO.getDefenseType(), commandDTO.getSourceIp());
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
            DefenseCommandDTO commandDTO = new DefenseCommandDTO();
            commandDTO.setSourceIp(target);
            commandDTO.setDefenseType(parseDefenseType(defenseType));
            commandDTO.setRiskLevel(DefenseCommandDTO.RiskLevel.HIGH);
            commandDTO.setDescription(reason);

            if (commandDTO.getDefenseType() == DefenseCommandDTO.DefenseType.RATE_LIMIT) {
                commandDTO.setRateLimitThreshold(DEFAULT_RATE_LIMIT_THRESHOLD);
            }

            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(120);
            commandDTO.setExpireTimestamp(
                    expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            );

            gatewayApiClient.pushDefenseCommand(commandDTO);

            log.info("生成手动防御决策：type={}, target={}, reason={}", defenseType, target, reason);
            return commandDTO;
        } catch (Exception e) {
            log.error("生成手动防御决策失败：", e);
            return null;
        }
    }

    private DefenseCommandDTO buildDefenseCommand(AttackMonitorDTO attackDTO,
                                                  DefenseCommandDTO.DefenseType defenseType,
                                                  String target,
                                                  String reason,
                                                  String expireTime) {
        DefenseCommandDTO dto = new DefenseCommandDTO();
        dto.setSourceIp(target);
        dto.setDefenseType(defenseType);
        dto.setRiskLevel(parseRiskLevel(attackDTO.getRiskLevel()));
        dto.setDescription(reason);
        dto.setEventId(String.valueOf(attackDTO.getTrafficId()));

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
