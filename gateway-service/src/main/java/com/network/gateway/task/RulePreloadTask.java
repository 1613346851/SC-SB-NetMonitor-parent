package com.network.gateway.task;

import com.network.gateway.cache.RuleCache;
import com.network.gateway.client.MonitorServiceConfigClient;
import com.network.gateway.dto.AttackRuleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 规则预加载任务
 * 在网关启动时从监测服务拉取规则
 */
@Component
@Order(2)
public class RulePreloadTask implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RulePreloadTask.class);

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private MonitorServiceConfigClient configClient;

    @Override
    public void run(String... args) {
        logger.info("开始执行规则预加载任务...");
        
        try {
            preloadRules();
            logger.info("规则预加载任务执行完成，共加载 {} 条规则", ruleCache.size());
        } catch (Exception e) {
            logger.error("规则预加载任务执行失败：", e);
        }
    }

    /**
     * 预加载规则
     */
    @SuppressWarnings("unchecked")
    private void preloadRules() {
        try {
            logger.info("从监测服务拉取规则...");
            
            Map<String, Object> response = configClient.getRules();
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Object data = response.get("data");
                if (data instanceof List) {
                    List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) data;
                    
                    for (Map<String, Object> ruleMap : ruleMaps) {
                        try {
                            AttackRuleDTO ruleDTO = convertToRuleDTO(ruleMap);
                            if (ruleDTO != null && ruleDTO.getEnabled() != null && ruleDTO.getEnabled() == 1) {
                                ruleCache.addRule(ruleDTO);
                            }
                        } catch (Exception e) {
                            logger.warn("转换规则失败: {}", ruleMap, e);
                        }
                    }
                    
                    logger.info("从监测服务拉取规则成功，共 {} 条", ruleMaps.size());
                }
            } else {
                logger.warn("从监测服务拉取规则失败，使用空规则缓存");
            }
        } catch (Exception e) {
            logger.warn("从监测服务拉取规则失败，使用空规则缓存: {}", e.getMessage());
        }
    }

    /**
     * 将Map转换为AttackRuleDTO
     */
    private AttackRuleDTO convertToRuleDTO(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        AttackRuleDTO dto = new AttackRuleDTO();
        
        if (map.get("id") != null) {
            dto.setId(Long.valueOf(map.get("id").toString()));
        }
        if (map.get("ruleName") != null) {
            dto.setRuleName(map.get("ruleName").toString());
        }
        if (map.get("ruleContent") != null) {
            dto.setRuleContent(map.get("ruleContent").toString());
        }
        if (map.get("attackType") != null) {
            dto.setAttackType(map.get("attackType").toString());
        }
        if (map.get("riskLevel") != null) {
            dto.setRiskLevel(map.get("riskLevel").toString());
        }
        if (map.get("enabled") != null) {
            dto.setEnabled(Integer.valueOf(map.get("enabled").toString()));
        }
        if (map.get("priority") != null) {
            dto.setPriority(Integer.valueOf(map.get("priority").toString()));
        }
        if (map.get("description") != null) {
            dto.setDescription(map.get("description").toString());
        }
        
        return dto;
    }
}
