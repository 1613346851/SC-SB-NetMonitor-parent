package com.network.monitor.service;

import com.network.monitor.dto.AlertDTO;
import com.network.monitor.dto.PageResult;
import com.network.monitor.entity.AlertEntity;
import com.network.monitor.entity.AlertRuleEntity;
import com.network.monitor.mapper.AlertMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警服务接口
 */
public interface AlertService {

    void generateAlert(AlertDTO alertDTO);

    void generateAlertFromAttack(Long attackId, String eventId, String sourceIp, String attackType, String riskLevel);

    AlertEntity getById(Long id);

    AlertEntity getByAlertId(String alertId);

    PageResult<AlertEntity> queryAlerts(String alertLevel, Integer status, String sourceIp,
                                         String attackType, LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size, String orderBy);

    List<AlertEntity> getPendingAlerts(int limit);

    List<AlertEntity> getUnnotifiedAlerts(int limit);

    void confirm(Long id, String confirmBy);

    void ignore(Long id, String ignoreBy, String ignoreReason);

    void batchConfirm(List<Long> ids, String confirmBy);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    long countPending();

    long countByLevel(String alertLevel);

    List<AlertMapper.LevelCountStat> countByAlertLevel(LocalDateTime startTime, LocalDateTime endTime);

    List<AlertMapper.StatusCountStat> countByStatus();

    AlertRuleEntity getRuleById(Long id);

    AlertRuleEntity getRuleByCode(String ruleCode);

    List<AlertRuleEntity> getAllRules();

    List<AlertRuleEntity> getEnabledRules();

    void updateRule(AlertRuleEntity rule);

    void toggleRule(Long id, boolean enabled);
}
