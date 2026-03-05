package com.network.monitor.service.impl;

import com.network.monitor.bo.DefenseResultBO;
import com.network.monitor.common.constant.AttackTypeConstant;
import com.network.monitor.common.constant.RiskLevelConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.service.AttackDetectService;
import com.network.monitor.service.AttackStoreService;
import com.network.monitor.service.DefenseDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 攻击检测服务实现类
 * 基于规则引擎的检测结果，完成通用 Web 攻击的最终判定
 */
@Slf4j
@Service
public class AttackDetectServiceImpl implements AttackDetectService {

    @Autowired
    private AttackStoreService attackStoreService;

    @Autowired
    private DefenseDecisionService defenseDecisionService;

    @Override
    public List<AttackMonitorDTO> detect(TrafficMonitorDTO trafficDTO) {
        if (trafficDTO == null) {
            return new ArrayList<>();
        }

        List<AttackMonitorDTO> allAttacks = new ArrayList<>();

        try {
            // 执行各类攻击检测
            allAttacks.addAll(detectSqlInjection(trafficDTO));
            allAttacks.addAll(detectXss(trafficDTO));
            allAttacks.addAll(detectCommandInjection(trafficDTO));

            if (!allAttacks.isEmpty()) {
                log.info("攻击检测完成：sourceIp={}, 检测到攻击数={}", 
                        trafficDTO.getSourceIp(), allAttacks.size());
            }
        } catch (Exception e) {
            log.error("攻击检测失败：sourceIp={}", trafficDTO.getSourceIp(), e);
        }

        return allAttacks;
    }

    @Override
    public List<AttackMonitorDTO> detectSqlInjection(TrafficMonitorDTO trafficDTO) {
        // SQL 注入检测逻辑已集成到 RuleEngineService 中
        // 这里预留扩展点，用于后续增强 SQL 注入检测
        return new ArrayList<>();
    }

    @Override
    public List<AttackMonitorDTO> detectXss(TrafficMonitorDTO trafficDTO) {
        // XSS 检测逻辑已集成到 RuleEngineService 中
        // 这里预留扩展点，用于后续增强 XSS 检测
        return new ArrayList<>();
    }

    @Override
    public List<AttackMonitorDTO> detectCommandInjection(TrafficMonitorDTO trafficDTO) {
        // 命令注入检测逻辑已集成到 RuleEngineService 中
        // 这里预留扩展点，用于后续增强命令注入检测
        return new ArrayList<>();
    }

    /**
     * 处理检测到的攻击
     * 封装标准化攻击记录，保存至数据库，推送防御决策
     *
     * @param attackDTO 攻击检测 DTO
     * @param trafficId 关联的流量 ID
     * @return 防御结果
     */
    public DefenseResultBO processDetectedAttack(AttackMonitorDTO attackDTO, Long trafficId) {
        DefenseResultBO result = new DefenseResultBO();

        try {
            // 设置关联的流量 ID
            attackDTO.setTrafficId(trafficId);

            // 保存攻击记录到数据库
            AttackMonitorEntity attackEntity = attackStoreService.convertToEntity(attackDTO);
            Long attackId = attackStoreService.saveAttack(attackEntity);

            if (attackId != null) {
                result.setDefenseId(attackId);
                result.setAttackId(attackId);
                result.setTrafficId(trafficId);
                result.markSuccess();

                log.info("保存攻击记录成功：attackId={}, attackType={}, riskLevel={}", 
                        attackId, attackDTO.getAttackType(), attackDTO.getRiskLevel());

                // 生成防御决策（高风险攻击）
                if (RiskLevelConstant.HIGH.equals(attackDTO.getRiskLevel()) || 
                    RiskLevelConstant.CRITICAL.equals(attackDTO.getRiskLevel())) {
                    defenseDecisionService.generateDefenseDecision(attackDTO);
                    result.setDefenseType("IP_BLOCK");
                }
            } else {
                result.markFailed("保存攻击记录失败");
            }
        } catch (Exception e) {
            log.error("处理检测到的攻击失败：", e);
            result.markFailed("处理攻击失败：" + e.getMessage());
        }

        return result;
    }

    /**
     * 构建攻击检测 DTO
     *
     * @param trafficDTO  流量数据
     * @param attackType  攻击类型
     * @param riskLevel   风险等级
     * @param confidence  置信度
     * @param ruleId      命中规则 ID
     * @param ruleContent 命中规则内容
     * @param attackContent 攻击内容
     * @return 攻击检测 DTO
     */
    protected AttackMonitorDTO buildAttackDTO(TrafficMonitorDTO trafficDTO,
                                               String attackType,
                                               String riskLevel,
                                               int confidence,
                                               Long ruleId,
                                               String ruleContent,
                                               String attackContent) {
        AttackMonitorDTO dto = new AttackMonitorDTO();

        dto.setTrafficId(null); // 后续填充
        dto.setAttackType(attackType);
        dto.setRiskLevel(riskLevel);
        dto.setConfidence(confidence);
        dto.setRuleId(ruleId);
        dto.setRuleContent(ruleContent);
        dto.setSourceIp(trafficDTO.getSourceIp());
        dto.setTargetUri(trafficDTO.getRequestUri());
        dto.setAttackContent(truncateContent(attackContent, 500));

        return dto;
    }

    /**
     * 截断内容
     *
     * @param content 内容
     * @param maxLength 最大长度
     * @return 截断后的内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
