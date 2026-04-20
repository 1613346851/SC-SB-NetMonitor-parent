package com.network.monitor.task;

import com.network.monitor.cache.RuleCache;
import com.network.monitor.cache.VulnerabilityCache;
import com.network.monitor.cache.BlacklistCache;
import com.network.monitor.service.RuleSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CachePreloadTask implements CommandLineRunner {

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private VulnerabilityCache vulnerabilityCache;

    @Autowired
    private BlacklistCache blacklistCache;

    @Autowired
    private RuleSyncService ruleSyncService;

    @Override
    public void run(String... args) {
        log.info("开始执行缓存预加载任务...");
        
        try {
            preloadRuleCache();
            
            preloadVulnerabilityCache();
            
            preloadBlacklistCache();
            
            syncRulesToGateway();
            
            log.info("缓存预加载任务执行完成");
        } catch (Exception e) {
            log.error("缓存预加载任务执行失败：", e);
        }
    }

    private void preloadRuleCache() {
        try {
            log.info("开始预加载规则缓存...");
            ruleCache.refreshCache();
            int ruleCount = ruleCache.getAllRules().size();
            log.info("规则缓存预加载完成，共加载 {} 条规则", ruleCount);
        } catch (Exception e) {
            log.error("规则缓存预加载失败：", e);
        }
    }

    private void preloadVulnerabilityCache() {
        try {
            log.info("开始预加载漏洞缓存...");
            vulnerabilityCache.refreshCache();
            java.util.List<com.network.monitor.entity.VulnerabilityMonitorEntity> vulns = vulnerabilityCache.getAllVulnerabilities();
            int vulnCount = vulns != null ? vulns.size() : 0;
            log.info("漏洞缓存预加载完成，共加载 {} 条漏洞", vulnCount);
        } catch (Exception e) {
            log.error("漏洞缓存预加载失败：", e);
        }
    }

    private void preloadBlacklistCache() {
        try {
            log.info("开始预加载黑名单缓存...");
            int blacklistSize = blacklistCache.getSize();
            log.info("黑名单缓存预加载完成，共加载 {} 条记录", blacklistSize);
        } catch (Exception e) {
            log.error("黑名单缓存预加载失败：", e);
        }
    }

    private void syncRulesToGateway() {
        try {
            log.info("开始同步规则到网关...");
            boolean success = ruleSyncService.syncAllRulesToGateway();
            if (success) {
                log.info("规则同步到网关成功");
            } else {
                log.warn("规则同步到网关失败，网关可能未启动");
            }
        } catch (Exception e) {
            log.error("规则同步到网关异常：", e);
        }
    }
}
