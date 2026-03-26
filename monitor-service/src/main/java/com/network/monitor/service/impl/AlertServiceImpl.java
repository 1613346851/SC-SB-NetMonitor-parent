package com.network.monitor.service.impl;

import com.network.monitor.dto.AlertDTO;
import com.network.monitor.dto.PageResult;
import com.network.monitor.entity.AlertEntity;
import com.network.monitor.entity.AlertRuleEntity;
import com.network.monitor.mapper.AlertMapper;
import com.network.monitor.mapper.AlertRuleMapper;
import com.network.monitor.service.AlertService;
import com.network.monitor.service.AlertSuppressService;
import com.network.monitor.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertServiceImpl.class);

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @Autowired
    private AlertSuppressService suppressService;

    @Autowired
    private SysConfigService sysConfigService;

    @Override
    @Transactional
    public void generateAlert(AlertDTO alertDTO) {
        if (!isAlertEnabled()) {
            logger.debug("告警功能已禁用，跳过告警生成");
            return;
        }

        if (suppressService.shouldSuppress(alertDTO.getSourceIp(), alertDTO.getAttackType())) {
            logger.info("告警被抑制: sourceIp={}, attackType={}", alertDTO.getSourceIp(), alertDTO.getAttackType());
            return;
        }

        AlertEntity existingAlert = findExistingAlert(alertDTO.getSourceIp(), alertDTO.getAttackType());
        if (existingAlert != null) {
            alertMapper.incrementAggregateCount(existingAlert.getId(), LocalDateTime.now());
            logger.info("告警已聚合: alertId={}, aggregateCount={}", existingAlert.getAlertId(), existingAlert.getAggregateCount() + 1);
            return;
        }

        AlertEntity alert = buildAlertEntity(alertDTO);
        alertMapper.insert(alert);
        
        suppressService.recordAlert(alertDTO.getSourceIp(), alertDTO.getAttackType());
        
        logger.info("告警已生成: alertId={}, level={}, sourceIp={}", 
            alert.getAlertId(), alert.getAlertLevel(), alert.getSourceIp());
    }

    @Override
    @Transactional
    public void generateAlertFromAttack(Long attackId, String eventId, String sourceIp, 
                                         String attackType, String riskLevel) {
        AlertDTO alertDTO = AlertDTO.fromAttack(eventId, sourceIp, attackType, riskLevel);
        generateAlert(alertDTO);
    }

    @Override
    public AlertEntity getById(Long id) {
        return alertMapper.selectById(id);
    }

    @Override
    public AlertEntity getByAlertId(String alertId) {
        return alertMapper.selectByAlertId(alertId);
    }

    @Override
    public PageResult<AlertEntity> queryAlerts(String alertLevel, Integer status, String sourceIp,
                                                String attackType, LocalDateTime startTime, LocalDateTime endTime,
                                                int page, int size, String orderBy) {
        int offset = (page - 1) * size;
        List<AlertEntity> list = alertMapper.selectByCondition(
            alertLevel, status, sourceIp, attackType, null, startTime, endTime, offset, size, orderBy);
        long total = alertMapper.countByCondition(
            alertLevel, status, sourceIp, attackType, null, startTime, endTime);
        return PageResult.of(list, total, page, size);
    }

    @Override
    public List<AlertEntity> getPendingAlerts(int limit) {
        return alertMapper.selectPendingAlerts(limit);
    }

    @Override
    public List<AlertEntity> getUnnotifiedAlerts(int limit) {
        return alertMapper.selectUnnotifiedAlerts(limit);
    }

    @Override
    @Transactional
    public void confirm(Long id, String confirmBy) {
        alertMapper.confirm(id, confirmBy, LocalDateTime.now());
        logger.info("告警已确认: id={}, confirmBy={}", id, confirmBy);
    }

    @Override
    @Transactional
    public void ignore(Long id, String ignoreBy, String ignoreReason) {
        alertMapper.ignore(id, ignoreBy, ignoreReason, LocalDateTime.now());
        logger.info("告警已忽略: id={}, ignoreBy={}, reason={}", id, ignoreBy, ignoreReason);
    }

    @Override
    @Transactional
    public void batchConfirm(List<Long> ids, String confirmBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        alertMapper.batchConfirm(ids, confirmBy, LocalDateTime.now());
        logger.info("批量确认告警: ids={}, confirmBy={}", ids, confirmBy);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        alertMapper.deleteById(id);
        logger.info("告警已删除: id={}", id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        alertMapper.deleteByIds(ids);
        logger.info("批量删除告警: ids={}", ids);
    }

    @Override
    public long countPending() {
        return alertMapper.countByCondition(null, 0, null, null, null, null, null);
    }

    @Override
    public long countByLevel(String alertLevel) {
        return alertMapper.countByCondition(alertLevel, null, null, null, null, null, null);
    }

    @Override
    public List<AlertMapper.LevelCountStat> countByAlertLevel(LocalDateTime startTime, LocalDateTime endTime) {
        return alertMapper.countByAlertLevel(startTime, endTime);
    }

    @Override
    public List<AlertMapper.StatusCountStat> countByStatus() {
        return alertMapper.countByStatus();
    }

    @Override
    public AlertRuleEntity getRuleById(Long id) {
        return alertRuleMapper.selectById(id);
    }

    @Override
    public AlertRuleEntity getRuleByCode(String ruleCode) {
        return alertRuleMapper.selectByRuleCode(ruleCode);
    }

    @Override
    public List<AlertRuleEntity> getAllRules() {
        return alertRuleMapper.selectAll();
    }

    @Override
    public List<AlertRuleEntity> getEnabledRules() {
        return alertRuleMapper.selectEnabled();
    }

    @Override
    @Transactional
    public void updateRule(AlertRuleEntity rule) {
        alertRuleMapper.update(rule);
        logger.info("告警规则已更新: id={}", rule.getId());
    }

    @Override
    @Transactional
    public void toggleRule(Long id, boolean enabled) {
        alertRuleMapper.updateEnabled(id, enabled ? 1 : 0);
        logger.info("告警规则状态已切换: id={}, enabled={}", id, enabled);
    }

    private boolean isAlertEnabled() {
        String enabled = sysConfigService.getConfigValue("alert.enabled");
        return "true".equalsIgnoreCase(enabled);
    }

    private AlertEntity findExistingAlert(String sourceIp, String attackType) {
        String suppressDurationStr = sysConfigService.getConfigValue("alert.aggregate.window-seconds");
        int suppressDuration = suppressDurationStr != null ? Integer.parseInt(suppressDurationStr) : 60;
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(suppressDuration);
        
        PageResult<AlertEntity> result = queryAlerts(null, 0, sourceIp, attackType, 
            threshold, null, 1, 1, "id DESC");
        
        return result.getList().isEmpty() ? null : result.getList().get(0);
    }

    private AlertEntity buildAlertEntity(AlertDTO dto) {
        AlertEntity entity = new AlertEntity();
        entity.setAlertId(UUID.randomUUID().toString());
        entity.setEventId(dto.getEventId());
        entity.setSourceIp(dto.getSourceIp());
        entity.setAttackType(dto.getAttackType());
        entity.setAlertLevel(dto.getAlertLevel());
        entity.setAlertTitle(dto.getAlertTitle());
        entity.setAlertContent(dto.getAlertContent());
        entity.setStatus(0);
        entity.setIsSuppressed(0);
        entity.setNotifyChannels(dto.getNotifyChannels() != null ? dto.getNotifyChannels() : "EMAIL,FEISHU");
        entity.setNotifyStatus(0);
        entity.setAggregateCount(1);
        entity.setFirstOccurTime(LocalDateTime.now());
        entity.setLastOccurTime(LocalDateTime.now());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
