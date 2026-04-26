package com.network.monitor.service.impl;

import com.network.monitor.common.util.AttackContentDecodeUtil;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.service.LocalCacheService;
import com.network.monitor.service.RuleManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则管理服务实现类
 */
@Slf4j
@Service
public class RuleManageServiceImpl implements RuleManageService {

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Autowired
    private LocalCacheService localCacheService;

    /**
     * 服务启动时预加载规则到缓存
     */
    @PostConstruct
    public void init() {
        loadRulesToCache();
        log.info("规则管理服务初始化完成，已加载启用规则到缓存");
    }

    @Override
    public void loadRulesToCache() {
        try {
            List<MonitorRuleEntity> enabledRules = monitorRuleMapper.selectAllEnabled();
            
            // 按攻击类型分类缓存
            for (MonitorRuleEntity rule : enabledRules) {
                String cacheKey = "cache:rule:" + rule.getAttackType() + ":" + rule.getId();
                localCacheService.put(cacheKey, rule, -1); // 永不过期
            }
            
            // 缓存所有规则列表
            localCacheService.put("cache:rule:all:enabled", enabledRules, -1);
            
            log.info("加载规则到缓存成功，数量：{}", enabledRules.size());
        } catch (Exception e) {
            log.error("加载规则到缓存失败：", e);
        }
    }

    @Override
    public List<MonitorRuleEntity> getRulesByType(String attackType) {
        if (attackType == null || attackType.isEmpty()) {
            return null;
        }
        
        // 尝试从缓存获取
        String cacheKey = "cache:rule:" + attackType;
        @SuppressWarnings("unchecked")
        List<MonitorRuleEntity> cachedRules = (List<MonitorRuleEntity>) localCacheService.get(cacheKey);
        
        if (cachedRules != null) {
            return cachedRules;
        }
        
        // 缓存未命中，从数据库查询
        List<MonitorRuleEntity> rules = monitorRuleMapper.selectByAttackType(attackType);
        
        // 写入缓存
        if (!rules.isEmpty()) {
            localCacheService.put(cacheKey, rules, -1);
        }
        
        return rules;
    }

    @Override
    public List<MonitorRuleEntity> getAllRules() {
        @SuppressWarnings("unchecked")
        List<MonitorRuleEntity> cachedRules = (List<MonitorRuleEntity>) localCacheService.get("cache:rule:all:enabled");
        
        if (cachedRules != null) {
            return cachedRules;
        }
        
        List<MonitorRuleEntity> rules = monitorRuleMapper.selectAllEnabled();
        localCacheService.put("cache:rule:all:enabled", rules, -1);
        
        return rules;
    }

    @Override
    public void refreshRuleCache() {
        clearRuleCache();
        loadRulesToCache();
        log.info("刷新规则缓存完成");
    }

    @Override
    public void clearRuleCache() {
        localCacheService.clearByPrefix("cache:rule:");
        log.info("清除规则缓存完成");
    }

    /**
     * 标准化处理规则内容（移除空白字符、SQL 注释等）
     */
    public String normalizeRuleContent(String ruleContent) {
        if (ruleContent == null || ruleContent.isEmpty()) {
            return ruleContent;
        }
        return AttackContentDecodeUtil.filterWhitespaceAndComments(ruleContent);
    }

    /**
     * 检查流量内容是否匹配规则
     */
    public boolean matchRule(String content, MonitorRuleEntity rule) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        String ruleContent = rule.getRuleContent();
        if (ruleContent == null || ruleContent.isEmpty()) {
            return false;
        }
        
        try {
            // 尝试正则匹配
            Pattern pattern = Pattern.compile(ruleContent, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        } catch (Exception e) {
            // 正则表达式无效时，使用关键词匹配
            return content.contains(ruleContent);
        }
    }
}
