package com.network.monitor.service.impl;

import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.common.constant.DefenseTypeConstant;
import com.network.monitor.common.constant.RiskLevelConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 防御决策生成服务实现类
 */
@Slf4j
@Service
public class DefenseDecisionServiceImpl implements DefenseDecisionService {

    @Autowired
    private GatewayApiClient gatewayApiClient;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO) {
        if (attackDTO == null) {
            return null;
        }

        try {
            String riskLevel = attackDTO.getRiskLevel();
            DefenseCommandDTO commandDTO = null;

            // 按风险等级分级处置
            if (RiskLevelConstant.HIGH.equals(riskLevel) || RiskLevelConstant.CRITICAL.equals(riskLevel)) {
                // 高风险：触发 IP 临时拉黑 + 恶意请求拦截
                commandDTO = buildDefenseCommand(
                    attackDTO,
                    DefenseTypeConstant.IP_BLOCK,
                    "ADD",
                    attackDTO.getSourceIp(),
                    "检测到高风险攻击：" + attackDTO.getAttackType(),
                    calculateExpireTime(30) // 30 分钟后过期
                );
            } else if (RiskLevelConstant.MEDIUM.equals(riskLevel)) {
                // 中风险：触发请求限流
                commandDTO = buildDefenseCommand(
                    attackDTO,
                    DefenseTypeConstant.RATE_LIMIT,
                    "ADD",
                    attackDTO.getSourceIp(),
                    "检测到中风险攻击：" + attackDTO.getAttackType(),
                    calculateExpireTime(60) // 60 分钟后过期
                );
            }
            // 低风险仅记录告警，不触发防御

            // 推送防御指令到网关
            if (commandDTO != null) {
                boolean success = gatewayApiClient.pushDefenseCommand(commandDTO);
                
                if (success) {
                    log.info("生成并推送防御决策成功：attackId={}, defenseType={}, target={}", 
                        attackDTO.getTrafficId(), commandDTO.getDefenseType(), commandDTO.getDefenseTarget());
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
            
            commandDTO.setDefenseType(defenseType);
            commandDTO.setDefenseAction("ADD");
            commandDTO.setDefenseTarget(target);
            commandDTO.setDefenseReason(reason);
            commandDTO.setExpireTime(calculateExpireTime(120)); // 手动操作默认 2 小时
            commandDTO.setRiskLevel(RiskLevelConstant.HIGH);

            // 推送至网关执行
            gatewayApiClient.pushDefenseCommand(commandDTO);

            log.info("生成手动防御决策：type={}, target={}, reason={}", defenseType, target, reason);
            
            return commandDTO;
        } catch (Exception e) {
            log.error("生成手动防御决策失败：", e);
            return null;
        }
    }

    /**
     * 构建防御指令 DTO
     */
    private DefenseCommandDTO buildDefenseCommand(AttackMonitorDTO attackDTO,
                                                   String defenseType,
                                                   String action,
                                                   String target,
                                                   String reason,
                                                   String expireTime) {
        DefenseCommandDTO dto = new DefenseCommandDTO();
        
        dto.setAttackId(attackDTO.getTrafficId());
        dto.setTrafficId(attackDTO.getTrafficId());
        dto.setDefenseType(defenseType);
        dto.setDefenseAction(action);
        dto.setDefenseTarget(target);
        dto.setDefenseReason(reason);
        dto.setExpireTime(expireTime);
        dto.setRiskLevel(attackDTO.getRiskLevel());
        
        return dto;
    }

    /**
     * 计算过期时间
     *
     * @param minutes 多少分钟后
     * @return 格式化后的过期时间字符串
     */
    private String calculateExpireTime(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes).format(TIME_FORMATTER);
    }
}
