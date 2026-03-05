package com.network.monitor.cache;

import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则缓存管理类
 * 专门负责攻击规则库的缓存管理
 */
@Slf4j
@Component
public class RuleCache {

    @Autowired
    private LocalCacheService localCacheService;

    /**
     * 规则缓存前缀
     */
    private static final String RULE_CACHE_PREFIX = "cache:rule:";

    /**
     * 按攻击类型缓存的规则
     * Key: attackType
     * Value: List<MonitorRuleEntity>
     */
    private final Map<String, List<MonitorRuleEntity>> rulesByType = new ConcurrentHashMap<>();

    /**
     * 按规则 ID 缓存的规则
     * Key: ruleId
     * Value: MonitorRuleEntity
     */
    private final Map<Long, MonitorRuleEntity> rulesById = new ConcurrentHashMap<>();

    /**
     * 服务启动时加载规则到缓存
     */
    @PostConstruct
    public void init() {
        log.info("规则缓存管理器初始化完成");
    }

    /**
     * 加载规则到缓存
     *
     * @param rules 规则列表
     */
    public void loadRules(List<MonitorRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            log.warn("规则列表为空，跳过加载");
            return;
        }

        // 清空现有缓存
        clear();

        // 按攻击类型分类缓存
        Map<String, List<MonitorRuleEntity>> rulesGroupByType = new ConcurrentHashMap<>();
        for (MonitorRuleEntity rule : rules) {
            // 按 ID 缓存
            if (rule.getId() != null) {
                rulesById.put(rule.getId(), rule);
            }

            // 按类型分组
            String attackType = rule.getAttackType();
            if (attackType != null && !attackType.isEmpty()) {
                rulesGroupByType.computeIfAbsent(attackType, k -> new ArrayList<>()).add(rule);
            }
        }

        // 缓存到本地和全局缓存
        rulesByType.putAll(rulesGroupByType);
        for (Map.Entry<String, List<MonitorRuleEntity>> entry : rulesGroupByType.entrySet()) {
            String cacheKey = RULE_CACHE_PREFIX + entry.getKey();
            localCacheService.put(cacheKey, entry.getValue(), -1);
        }

        // 缓存所有启用规则列表
        localCacheService.put(RULE_CACHE_PREFIX + "all:enabled", rules, -1);

        log.info("加载规则到缓存成功，总数：{}, 类型数：{}", rules.size(), rulesGroupByType.size());
    }

    /**
     * 根据攻击类型获取规则列表
     *
     * @param attackType 攻击类型
     * @return 规则列表
     */
    public List<MonitorRuleEntity> getRulesByType(String attackType) {
        if (attackType == null || attackType.isEmpty()) {
            return new ArrayList<>();
        }

        // 先从内存缓存获取
        List<MonitorRuleEntity> cachedRules = rulesByType.get(attackType);
        if (cachedRules != null && !cachedRules.isEmpty()) {
            return cachedRules;
        }

        // 从本地缓存服务获取
        String cacheKey = RULE_CACHE_PREFIX + attackType;
        @SuppressWarnings("unchecked")
        List<MonitorRuleEntity> rules = (List<MonitorRuleEntity>) localCacheService.get(cacheKey);
        
        if (rules != null) {
            rulesByType.put(attackType, rules);
            return rules;
        }

        return new ArrayList<>();
    }

    /**
     * 获取所有规则
     *
     * @return 所有规则列表
     */
    public List<MonitorRuleEntity> getAllRules() {
        @SuppressWarnings("unchecked")
        List<MonitorRuleEntity> allRules = (List<MonitorRuleEntity>) localCacheService.get(RULE_CACHE_PREFIX + "all:enabled");
        return allRules != null ? allRules : new ArrayList<>();
    }

    /**
     * 根据 ID 获取规则
     *
     * @param ruleId 规则 ID
     * @return 规则实体
     */
    public MonitorRuleEntity getRuleById(Long ruleId) {
        if (ruleId == null) {
            return null;
        }

        MonitorRuleEntity rule = rulesById.get(ruleId);
        if (rule == null) {
            // 尝试从缓存中加载
            @SuppressWarnings("unchecked")
            List<MonitorRuleEntity> allRules = getAllRules();
            for (MonitorRuleEntity r : allRules) {
                if (ruleId.equals(r.getId())) {
                    rule = r;
                    rulesById.put(ruleId, r);
                    break;
                }
            }
        }

        return rule;
    }

    /**
     * 添加规则到缓存
     *
     * @param rule 规则实体
     */
    public void addRule(MonitorRuleEntity rule) {
        if (rule == null || rule.getId() == null) {
            return;
        }

        rulesById.put(rule.getId(), rule);

        String attackType = rule.getAttackType();
        if (attackType != null && !attackType.isEmpty()) {
            rulesByType.computeIfAbsent(attackType, k -> new ArrayList<>()).add(rule);
        }

        log.debug("添加规则到缓存：ruleId={}, ruleName={}", rule.getId(), rule.getRuleName());
    }

    /**
     * 从缓存移除规则
     *
     * @param ruleId 规则 ID
     */
    public void removeRule(Long ruleId) {
        if (ruleId == null) {
            return;
        }

        MonitorRuleEntity rule = rulesById.remove(ruleId);
        if (rule != null && rule.getAttackType() != null) {
            List<MonitorRuleEntity> typeRules = rulesByType.get(rule.getAttackType());
            if (typeRules != null) {
                typeRules.removeIf(r -> r.getId().equals(ruleId));
            }
        }

        log.debug("从缓存移除规则：ruleId={}", ruleId);
    }

    /**
     * 更新缓存中的规则
     *
     * @param rule 更新后的规则
     */
    public void updateRule(MonitorRuleEntity rule) {
        if (rule == null || rule.getId() == null) {
            return;
        }

        // 先移除旧的
        removeRule(rule.getId());

        // 添加新的
        addRule(rule);

        log.debug("更新缓存中的规则：ruleId={}", rule.getId());
    }

    /**
     * 清空所有规则缓存
     */
    public void clear() {
        rulesByType.clear();
        rulesById.clear();
        localCacheService.clearByPrefix(RULE_CACHE_PREFIX);
        log.info("清空所有规则缓存");
    }

    /**
     * 刷新规则缓存
     */
    public void refreshCache() {
        clear();
        log.info("规则缓存已刷新");
    }

    /**
     * 获取缓存的规则总数
     *
     * @return 规则总数
     */
    public int getTotalCount() {
        return rulesById.size();
    }

    /**
     * 获取缓存的攻击类型数
     *
     * @return 攻击类型数
     */
    public int getTypeCount() {
        return rulesByType.size();
    }
}
