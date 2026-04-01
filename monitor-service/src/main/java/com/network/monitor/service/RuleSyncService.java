package com.network.monitor.service;

import com.network.monitor.client.MonitorServiceRuleClient;
import com.network.monitor.dto.RuleSyncDTO;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则同步服务
 * 负责将规则变更同步到网关服务
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Slf4j
@Service
public class RuleSyncService {

    @Autowired
    private MonitorServiceRuleClient ruleClient;

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    private volatile boolean syncEnabled = true;

    @PostConstruct
    public void init() {
        log.info("规则同步服务初始化完成");
    }

    /**
     * 同步单个规则到网关
     *
     * @param rule      规则实体
     * @param operation 操作类型：ADD, UPDATE, DELETE
     * @return 是否成功
     */
    public boolean syncRuleToGateway(MonitorRuleEntity rule, String operation) {
        if (!syncEnabled) {
            log.debug("规则同步已禁用，跳过同步");
            return false;
        }

        try {
            RuleSyncDTO ruleDTO = convertToSyncDTO(rule, operation);
            boolean success = ruleClient.pushRuleToGatewayNoRetry(ruleDTO);

            if (success) {
                log.info("规则同步成功: id={}, name={}, operation={}", rule.getId(), rule.getRuleName(), operation);
            } else {
                log.warn("规则同步失败: id={}, name={}, operation={}", rule.getId(), rule.getRuleName(), operation);
            }

            return success;
        } catch (Exception e) {
            log.error("规则同步异常: id={}, name={}", rule.getId(), rule.getRuleName(), e);
            return false;
        }
    }

    /**
     * 异步同步单个规则到网关
     *
     * @param rule      规则实体
     * @param operation 操作类型
     */
    @Async
    public void syncRuleToGatewayAsync(MonitorRuleEntity rule, String operation) {
        syncRuleToGateway(rule, operation);
    }

    /**
     * 同步规则删除到网关
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    public boolean syncRuleDeleteToGateway(Long ruleId) {
        if (!syncEnabled) {
            log.debug("规则同步已禁用，跳过同步");
            return false;
        }

        try {
            boolean success = ruleClient.deleteRuleFromGateway(ruleId);

            if (success) {
                log.info("规则删除同步成功: id={}", ruleId);
            } else {
                log.warn("规则删除同步失败: id={}", ruleId);
            }

            return success;
        } catch (Exception e) {
            log.error("规则删除同步异常: id={}", ruleId, e);
            return false;
        }
    }

    /**
     * 异步同步规则删除到网关
     *
     * @param ruleId 规则ID
     */
    @Async
    public void syncRuleDeleteToGatewayAsync(Long ruleId) {
        syncRuleDeleteToGateway(ruleId);
    }

    /**
     * 批量同步所有启用的规则到网关
     *
     * @return 是否成功
     */
    public boolean syncAllRulesToGateway() {
        if (!syncEnabled) {
            log.debug("规则同步已禁用，跳过同步");
            return false;
        }

        try {
            List<MonitorRuleEntity> enabledRules = monitorRuleMapper.selectAllEnabled();

            if (enabledRules == null || enabledRules.isEmpty()) {
                log.info("没有启用的规则需要同步");
                return true;
            }

            List<RuleSyncDTO> ruleDTOs = enabledRules.stream()
                    .map(rule -> convertToSyncDTO(rule, "UPDATE"))
                    .collect(Collectors.toList());

            boolean success = ruleClient.pushRulesToGateway(ruleDTOs);

            if (success) {
                log.info("批量同步规则成功: count={}", enabledRules.size());
            } else {
                log.warn("批量同步规则失败");
            }

            return success;
        } catch (Exception e) {
            log.error("批量同步规则异常", e);
            return false;
        }
    }

    /**
     * 异步批量同步所有启用的规则到网关
     */
    @Async
    public void syncAllRulesToGatewayAsync() {
        syncAllRulesToGateway();
    }

    /**
     * 同步规则状态变更到网关
     *
     * @param ruleId      规则ID
     * @param newEnabled  新的启用状态
     * @return 是否成功
     */
    public boolean syncRuleStatusToGateway(Long ruleId, Integer newEnabled) {
        if (!syncEnabled) {
            log.debug("规则同步已禁用，跳过同步");
            return false;
        }

        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(ruleId);
            if (rule == null) {
                log.warn("规则不存在，无法同步状态: id={}", ruleId);
                return false;
            }

            rule.setEnabled(newEnabled);
            String operation = newEnabled == 1 ? "UPDATE" : "DELETE";

            return syncRuleToGateway(rule, operation);
        } catch (Exception e) {
            log.error("同步规则状态变更异常: id={}", ruleId, e);
            return false;
        }
    }

    /**
     * 将规则实体转换为同步DTO
     *
     * @param rule      规则实体
     * @param operation 操作类型
     * @return RuleSyncDTO
     */
    private RuleSyncDTO convertToSyncDTO(MonitorRuleEntity rule, String operation) {
        RuleSyncDTO dto = new RuleSyncDTO();
        dto.setId(rule.getId());
        dto.setRuleName(rule.getRuleName());
        dto.setAttackType(rule.getAttackType());
        dto.setRuleContent(rule.getRuleContent());
        dto.setDescription(rule.getDescription());
        dto.setRiskLevel(rule.getRiskLevel());
        dto.setEnabled(rule.getEnabled());
        dto.setPriority(rule.getPriority() != null ? rule.getPriority() : 100);
        dto.setTimestamp(System.currentTimeMillis());
        dto.setOperation(operation);
        return dto;
    }

    /**
     * 启用规则同步
     */
    public void enableSync() {
        this.syncEnabled = true;
        log.info("规则同步已启用");
    }

    /**
     * 禁用规则同步
     */
    public void disableSync() {
        this.syncEnabled = false;
        log.info("规则同步已禁用");
    }

    /**
     * 获取同步状态
     *
     * @return 是否启用
     */
    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    /**
     * 获取服务统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("规则同步服务 - 同步状态: %s, 客户端信息: %s",
                syncEnabled ? "启用" : "禁用",
                ruleClient.getStatistics());
    }
}
