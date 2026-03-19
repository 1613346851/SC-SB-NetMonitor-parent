package com.network.monitor.service;

import com.network.monitor.entity.MonitorRuleEntity;

import java.util.List;

/**
 * 规则管理服务接口
 */
public interface RuleManageService {

    /**
     * 加载所有启用的规则到缓存
     */
    void loadRulesToCache();

    /**
     * 根据攻击类型获取规则列表
     */
    List<MonitorRuleEntity> getRulesByType(String attackType);

    /**
     * 获取所有规则
     */
    List<MonitorRuleEntity> getAllRules();

    /**
     * 更新规则缓存
     */
    void refreshRuleCache();

    /**
     * 清除规则缓存
     */
    void clearRuleCache();
}
