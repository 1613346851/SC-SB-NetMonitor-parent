package com.network.gateway.task;

import com.network.gateway.cache.RuleCache;
import com.network.gateway.cache.WhitelistCache;
import com.network.gateway.client.MonitorServiceRuleClient;
import com.network.gateway.dto.AttackRuleDTO;
import com.network.gateway.dto.WhitelistDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RulePreloadTask implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RulePreloadTask.class);

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private WhitelistCache whitelistCache;

    @Autowired
    private MonitorServiceRuleClient ruleClient;

    private final AtomicBoolean preloadCompleted = new AtomicBoolean(false);

    private final AtomicBoolean whitelistPreloadCompleted = new AtomicBoolean(false);

    @Override
    public void run(String... args) {
        logger.info("开始执行规则预加载任务...");
        
        preloadRules();
        preloadWhitelists();
        
        logger.info("规则预加载任务执行完成");
    }

    private void preloadRules() {
        int maxRetries = 5;
        int retryInterval = 5000;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                logger.info("尝试从监控服务拉取规则，第{}次...", i + 1);
                
                List<AttackRuleDTO> rules = ruleClient.pullRulesFromMonitor();
                
                if (rules != null && !rules.isEmpty()) {
                    ruleCache.syncRules(rules);
                    preloadCompleted.set(true);
                    logger.info("规则预加载成功，共加载{}条规则", rules.size());
                    return;
                } else {
                    logger.warn("从监控服务拉取规则为空，等待重试...");
                }
            } catch (Exception e) {
                logger.warn("从监控服务拉取规则失败: {}，等待重试...", e.getMessage());
            }
            
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        if (!preloadCompleted.get()) {
            logger.warn("规则预加载失败，网关将在收到监控服务推送规则后生效");
        }
    }

    private void preloadWhitelists() {
        int maxRetries = 5;
        int retryInterval = 5000;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                logger.info("尝试从监控服务拉取白名单，第{}次...", i + 1);
                
                List<WhitelistDTO> whitelists = ruleClient.pullWhitelistsFromMonitor();
                
                if (whitelists != null && !whitelists.isEmpty()) {
                    whitelistCache.syncWhitelists(whitelists);
                    whitelistPreloadCompleted.set(true);
                    logger.info("白名单预加载成功，共加载{}条", whitelists.size());
                    return;
                } else {
                    logger.warn("从监控服务拉取白名单为空，等待重试...");
                }
            } catch (Exception e) {
                logger.warn("从监控服务拉取白名单失败: {}，等待重试...", e.getMessage());
            }
            
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        if (!whitelistPreloadCompleted.get()) {
            logger.warn("白名单预加载失败，网关将在收到监控服务推送白名单后生效");
        }
    }

    public boolean isPreloadCompleted() {
        return preloadCompleted.get();
    }

    public boolean isWhitelistPreloadCompleted() {
        return whitelistPreloadCompleted.get();
    }

    public int getRuleCount() {
        return ruleCache.size();
    }

    public int getWhitelistCount() {
        return whitelistCache.size();
    }
}
