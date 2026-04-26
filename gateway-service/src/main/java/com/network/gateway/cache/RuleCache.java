package com.network.gateway.cache;

import com.network.gateway.dto.AttackRuleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 攻击规则缓存
 * 存储从监测服务同步的攻击检测规则
 * 按攻击类型分组，支持正则表达式预编译
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class RuleCache {

    private static final Logger logger = LoggerFactory.getLogger(RuleCache.class);

    private final Map<Long, AttackRuleDTO> ruleById = new ConcurrentHashMap<>();

    private final Map<String, List<AttackRuleDTO>> rulesByType = new ConcurrentHashMap<>();

    private final Map<Long, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    private volatile List<AttackRuleDTO> sortedAllRules = new ArrayList<>();

    private volatile long lastSyncTime = 0;

    private volatile int totalHitCount = 0;

    private volatile int totalMatchCount = 0;

    public synchronized void addRule(AttackRuleDTO rule) {
        if (rule == null || rule.getId() == null) {
            logger.warn("尝试添加空规则或无ID规则");
            return;
        }

        if (rule.getEnabled() == null || rule.getEnabled() != 1) {
            logger.debug("规则未启用，跳过: id={}, name={}", rule.getId(), rule.getRuleName());
            return;
        }

        if (rule.getRuleContent() == null || rule.getRuleContent().isEmpty()) {
            logger.warn("规则内容为空，跳过: id={}, name={}", rule.getId(), rule.getRuleName());
            return;
        }

        try {
            Pattern pattern = Pattern.compile(rule.getRuleContent(), Pattern.CASE_INSENSITIVE);
            compiledPatterns.put(rule.getId(), pattern);
        } catch (PatternSyntaxException e) {
            logger.error("规则正则表达式编译失败: id={}, name={}, pattern={}, error={}",
                    rule.getId(), rule.getRuleName(), rule.getRuleContent(), e.getMessage());
            return;
        }

        ruleById.put(rule.getId(), rule);

        String attackType = rule.getAttackType();
        if (attackType != null) {
            rulesByType.computeIfAbsent(attackType, k -> new ArrayList<>());
            List<AttackRuleDTO> typeRules = rulesByType.get(attackType);
            typeRules.removeIf(r -> r.getId().equals(rule.getId()));
            typeRules.add(rule);
            typeRules.sort(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 100));
        }

        rebuildSortedAllRules();

        lastSyncTime = System.currentTimeMillis();
        logger.info("添加规则成功: id={}, name={}, type={}", rule.getId(), rule.getRuleName(), attackType);
    }

    private void rebuildSortedAllRules() {
        sortedAllRules = new ArrayList<>(ruleById.values());
        sortedAllRules.sort(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 100));
    }

    public synchronized void updateRule(AttackRuleDTO rule) {
        if (rule == null || rule.getId() == null) {
            return;
        }

        removeRule(rule.getId());
        addRule(rule);
        logger.info("更新规则成功: id={}, name={}", rule.getId(), rule.getRuleName());
    }

    public synchronized void removeRule(Long ruleId) {
        if (ruleId == null) {
            return;
        }

        AttackRuleDTO removed = ruleById.remove(ruleId);
        if (removed != null) {
            compiledPatterns.remove(ruleId);

            String attackType = removed.getAttackType();
            if (attackType != null && rulesByType.containsKey(attackType)) {
                List<AttackRuleDTO> typeRules = rulesByType.get(attackType);
                typeRules.removeIf(r -> r.getId().equals(ruleId));
                if (typeRules.isEmpty()) {
                    rulesByType.remove(attackType);
                }
            }
            rebuildSortedAllRules();
            logger.info("移除规则成功: id={}, name={}", ruleId, removed.getRuleName());
        }
    }

    public synchronized void syncRules(List<AttackRuleDTO> rules) {
        if (rules == null) {
            logger.warn("同步规则列表为空");
            return;
        }

        clear();

        for (AttackRuleDTO rule : rules) {
            addRule(rule);
        }

        lastSyncTime = System.currentTimeMillis();
        logger.info("批量同步规则完成，共{}条规则，其中有效规则{}条", rules.size(), ruleById.size());
    }

    public void clear() {
        ruleById.clear();
        rulesByType.clear();
        compiledPatterns.clear();
        sortedAllRules = new ArrayList<>();
        logger.info("规则缓存已清空");
    }

    @PreDestroy
    public void destroy() {
        clear();
        logger.info("规则缓存已销毁");
    }

    public List<AttackRuleDTO> getAllRules() {
        return new ArrayList<>(ruleById.values());
    }

    public List<AttackRuleDTO> getRulesByType(String attackType) {
        List<AttackRuleDTO> rules = rulesByType.get(attackType);
        return rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    public Set<String> getAttackTypes() {
        return new HashSet<>(rulesByType.keySet());
    }

    public AttackRuleDTO getRuleById(Long id) {
        return ruleById.get(id);
    }

    public Pattern getCompiledPattern(Long ruleId) {
        return compiledPatterns.get(ruleId);
    }

    public MatchResult match(String content, String attackType) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        totalHitCount++;

        List<AttackRuleDTO> rules = rulesByType.get(attackType);
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (AttackRuleDTO rule : rules) {
            Pattern pattern = compiledPatterns.get(rule.getId());
            if (pattern == null) {
                continue;
            }

            try {
                if (pattern.matcher(content).find()) {
                    totalMatchCount++;
                    return new MatchResult(rule, content);
                }
            } catch (Exception e) {
                logger.error("规则匹配异常: ruleId={}, error={}", rule.getId(), e.getMessage());
            }
        }

        return null;
    }

    public MatchResult matchAll(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        totalHitCount++;

        for (AttackRuleDTO rule : sortedAllRules) {
            Pattern pattern = compiledPatterns.get(rule.getId());
            if (pattern == null) {
                continue;
            }

            try {
                if (pattern.matcher(content).find()) {
                    totalMatchCount++;
                    return new MatchResult(rule, content);
                }
            } catch (Exception e) {
                logger.error("规则匹配异常: ruleId={}, error={}", rule.getId(), e.getMessage());
            }
        }

        return null;
    }

    public int size() {
        return ruleById.size();
    }

    public int getTypeCount() {
        return rulesByType.size();
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public String getStats() {
        return String.format("规则缓存统计 - 总规则数:%d, 类型数:%d, 命中次数:%d, 匹配次数:%d, 最后同步时间:%d",
                ruleById.size(), rulesByType.size(), totalHitCount, totalMatchCount, lastSyncTime);
    }

    public Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", ruleById.size());
        stats.put("typeCount", rulesByType.size());
        stats.put("totalHitCount", totalHitCount);
        stats.put("totalMatchCount", totalMatchCount);
        stats.put("lastSyncTime", lastSyncTime);

        Map<String, Integer> typeDistribution = new HashMap<>();
        rulesByType.forEach((type, rules) -> typeDistribution.put(type, rules.size()));
        stats.put("typeDistribution", typeDistribution);

        return stats;
    }

    public static class MatchResult {
        private final AttackRuleDTO matchedRule;
        private final String matchedContent;
        private final long matchTime;

        public MatchResult(AttackRuleDTO matchedRule, String matchedContent) {
            this.matchedRule = matchedRule;
            this.matchedContent = matchedContent;
            this.matchTime = System.currentTimeMillis();
        }

        public AttackRuleDTO getMatchedRule() {
            return matchedRule;
        }

        public String getMatchedContent() {
            return matchedContent;
        }

        public long getMatchTime() {
            return matchTime;
        }

        public String getRuleName() {
            return matchedRule != null ? matchedRule.getRuleName() : "unknown";
        }

        public String getAttackType() {
            return matchedRule != null ? matchedRule.getAttackType() : "unknown";
        }

        public String getRiskLevel() {
            return matchedRule != null ? matchedRule.getRiskLevel() : "MEDIUM";
        }
    }
}
