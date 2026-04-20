package com.network.gateway.cache;

import com.network.gateway.client.MonitorServiceConfigClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关配置缓存
 * 从监控服务拉取配置并缓存到本地
 * 支持动态更新配置
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class GatewayConfigCache {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfigCache.class);

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @Autowired
    private MonitorServiceConfigClient configClient;

    @PostConstruct
    public void init() {
        logger.info("初始化网关配置缓存...");
        loadDefaultConfigs();
        pullFromMonitorService();
        logger.info("网关配置缓存初始化完成，共{}项配置", configCache.size());
    }

    @PreDestroy
    public void destroy() {
        configCache.clear();
        logger.info("网关配置缓存已清理");
    }

    private void loadDefaultConfigs() {
        configCache.put("gateway.defense.blacklist.enabled", "true");
        configCache.put("gateway.defense.rate-limit.enabled", "true");
        configCache.put("gateway.defense.malicious-request.enabled", "true");
        configCache.put("gateway.defense.rate-limit.default-threshold", "30");
        configCache.put("gateway.defense.rate-limit.window-size", "1000");
        configCache.put("gateway.defense.blacklist.default-expire-seconds", "600");
        configCache.put("gateway.defense.malicious.user-agents", 
                "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
        configCache.put("gateway.defense.malicious.uri-patterns", 
                "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
        configCache.put("gateway.cache.traffic-expire-ms", "3600000");
        configCache.put("gateway.cache.blacklist-expire-ms", "600000");
        configCache.put("gateway.cache.cleanup-interval-ms", "60000");
        configCache.put("gateway.attack-state.cooldown-duration-ms", "60000");
        configCache.put("gateway.attack-state.state-expire-ms", "600000");
        configCache.put("gateway.request.max-body-size", "102400");
        configCache.put("gateway.request.abnormal-response-threshold-ms", "3000");

        configCache.put("traffic.push.normal.strategy", "aggregate");
        configCache.put("traffic.push.suspicious.strategy", "aggregate");
        configCache.put("traffic.push.attacking.strategy", "aggregate");
        configCache.put("traffic.push.defended.strategy", "aggregate");
        configCache.put("traffic.push.cooldown.strategy", "aggregate");
        configCache.put("traffic.push.batch-interval-ms", "3000");
        configCache.put("traffic.push.enabled", "true");

        configCache.put("ddos.threshold", "50");
        configCache.put("ddos.detection.window-ms", "1000");
        configCache.put("ddos.rate-limit-trigger-count", "5");
        configCache.put("ddos.rate-limit-trigger-window-seconds", "30");

        configCache.put("state.normal-to-suspicious.threshold-rps", "30");
        configCache.put("state.normal-to-suspicious.window-ms", "1000");
        configCache.put("state.suspicious-to-attacking.duration-ms", "5000");
        configCache.put("state.suspicious-to-attacking.min-requests", "50");
        configCache.put("state.suspicious-to-attacking.uri-diversity-threshold", "3");
        configCache.put("state.suspicious-to-normal.quiet-duration-ms", "10000");
        configCache.put("state.defended-to-cooldown.quiet-duration-ms", "30000");
        configCache.put("state.cooldown.base-duration-ms", "180000");
        configCache.put("state.cooldown.max-duration-ms", "600000");
        configCache.put("state.cooldown.attack-intensity-multiplier", "0.5");
        configCache.put("state.cooldown-to-attacking.threshold-rps", "3");

        configCache.put("cooldown.dynamic.enabled", "true");
        configCache.put("cooldown.base-duration-ms", "180000");
        configCache.put("cooldown.max-duration-ms", "600000");
        configCache.put("cooldown.intensity-multiplier", "0.5");
        configCache.put("cooldown.history-multiplier", "0.2");
        configCache.put("cooldown.history-max-multiplier", "2.0");

        configCache.put("confidence.base-score", "30");
        configCache.put("confidence.frequency.max-score", "25");
        configCache.put("confidence.frequency.per-exceed-score", "5");
        configCache.put("confidence.diversity.max-score", "20");
        configCache.put("confidence.diversity.per-uri-score", "3");
        configCache.put("confidence.persistence.max-score", "15");
        configCache.put("confidence.persistence.per-10s-score", "3");
        configCache.put("confidence.pattern.max-score", "10");
        configCache.put("confidence.normal-behavior.max-deduction", "20");
        configCache.put("confidence.normal-behavior.no-history-deduction", "5");
        configCache.put("confidence.normal-behavior.normal-requests-deduction", "15");
        configCache.put("confidence.smooth.strategy", "ONLY_UP");
        configCache.put("confidence.smooth.alpha", "0.4");
        configCache.put("confidence.min-value", "10");

        configCache.put("traffic.push.interval-ms", "3000");
        configCache.put("traffic.sample.max-per-uri", "3");
        configCache.put("traffic.sample.max-total", "20");
        configCache.put("traffic.aggregate.uri-pattern-depth", "2");
        configCache.put("traffic.aggregate.max-uri-groups", "50");

        configCache.put("traffic.push.retry.max-count", "3");
        configCache.put("traffic.push.retry.delay-ms", "1000");
        configCache.put("traffic.push.retry.max-queue-size", "10000");
        configCache.put("traffic.push.memory.max-usage-percent", "80");
        configCache.put("traffic.push.memory.force-flush-threshold", "90");
        configCache.put("traffic.push.degradation.enabled", "true");
        configCache.put("traffic.push.degradation.local-cache-size", "50000");
        configCache.put("traffic.push.degradation.health-check-interval-ms", "30000");

        configCache.put("state.suspicious.timeout-ms", "30000");
        configCache.put("state.cooldown.timeout-ms", "600000");
        configCache.put("state.normal-to-suspicious.slide-step-ms", "100");

        configCache.put("traffic.push.interval-low-ms", "10000");
        configCache.put("traffic.sample.abnormal-priority", "true");
        configCache.put("traffic.sample.desensitize-enabled", "true");
        configCache.put("traffic.aggregate.batch-threshold", "10");
        configCache.put("traffic.queue.single-ip-capacity", "50");
        configCache.put("traffic.queue.global-capacity", "1000");
        configCache.put("traffic.queue.overflow-strategy", "DROP_OLDEST_SAMPLE");

        configCache.put("traffic.push.retry-interval-ms", "500,1000,2000");
        configCache.put("traffic.push.fallback-enabled", "true");

        configCache.put("traffic.business-peak.enabled", "true");
        configCache.put("traffic.business-peak.threshold-multiplier", "2");
        configCache.put("traffic.business-peak.time-ranges", "09:00-12:00,14:00-18:00");

        configCache.put("confidence.history.max-score", "10");
        configCache.put("confidence.history.no-attack-deduction", "5");
        configCache.put("confidence.history.has-attack-score", "10");
        configCache.put("confidence.history.normal-rate-deduction", "10");
        configCache.put("confidence.slow-attack.max-score", "10");
        configCache.put("confidence.slow-attack.per-minute-score", "5");
        configCache.put("confidence.global-attack.max-score", "10");
        configCache.put("confidence.global-attack.per-ip-score", "2");
        configCache.put("confidence.pattern.partial-score", "5");
        configCache.put("confidence.no-decrease.enabled", "true");
        configCache.put("confidence.blocked.rate-limit-score", "3");
        configCache.put("confidence.blocked.blacklist-score", "5");
        configCache.put("confidence.blocked.max-daily-score", "30");

        configCache.put("state.slow-attack.duration-ms", "60000");
        configCache.put("state.slow-attack.threshold-rps", "5");
        configCache.put("state.global-attack.related-ip-threshold", "5");
        configCache.put("state.global-attack.network-mask", "24");

        configCache.put("state.manual-reset.log-required", "true");
        configCache.put("state.manual-reset.operator-required", "true");

        configCache.put("gateway.defense.rate-limit.peak-threshold", "60");
        configCache.put("gateway.defense.ban.duration-base-ms", "300000");
        configCache.put("gateway.defense.ban.duration-multiplier", "6");

        configCache.put("ddos.slow-attack.threshold-rps", "5");
        configCache.put("ddos.global-attack.related-ip-threshold", "5");

        logger.info("加载默认配置完成，共{}项", configCache.size());
    }

    public boolean pullFromMonitorService() {
        try {
            Map<String, String> remoteConfigs = configClient.pullAllConfigs();
            if (remoteConfigs != null && !remoteConfigs.isEmpty()) {
                int updateCount = 0;
                for (Map.Entry<String, String> entry : remoteConfigs.entrySet()) {
                    String key = entry.getKey();
                    if (key != null && isValidConfigKey(key)) {
                        configCache.put(key, entry.getValue());
                        updateCount++;
                    }
                }
                logger.info("从监控服务拉取配置成功，更新{}项", updateCount);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("从监控服务拉取配置失败，使用本地默认配置: {}", e.getMessage());
            return false;
        }
    }

    private boolean isValidConfigKey(String key) {
        return key.startsWith("gateway.") ||
               key.startsWith("traffic.push.") ||
               key.startsWith("traffic.sample.") ||
               key.startsWith("traffic.aggregate.") ||
               key.startsWith("traffic.queue.") ||
               key.startsWith("traffic.business-peak.") ||
               key.startsWith("ddos.") ||
               key.startsWith("defense.") ||
               key.startsWith("state.") ||
               key.startsWith("cooldown.") ||
               key.startsWith("confidence.");
    }

    public void updateConfig(String configKey, String configValue) {
        if (configKey == null || configKey.isEmpty()) {
            logger.warn("尝试更新空配置键");
            return;
        }

        if (!isValidConfigKey(configKey)) {
            logger.warn("非网关配置项，忽略更新: {}", configKey);
            return;
        }

        configCache.put(configKey, configValue);
        logger.info("配置已更新: {} = {}", configKey, configValue);
    }

    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }

        int updateCount = 0;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            if (key != null && isValidConfigKey(key)) {
                configCache.put(key, entry.getValue());
                updateCount++;
            }
        }

        logger.info("批量更新配置完成，共{}项", updateCount);
    }

    public String getString(String configKey) {
        return configCache.get(configKey);
    }

    public String getString(String configKey, String defaultValue) {
        String value = configCache.get(configKey);
        return value != null ? value : defaultValue;
    }

    public int getInt(String configKey, int defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值转换整数失败: {} = {}, 使用默认值: {}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String configKey, long defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值转换长整数失败: {} = {}, 使用默认值: {}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String configKey, boolean defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public List<String> getList(String configKey, String defaultValue) {
        String value = configCache.get(configKey);
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(value.split(","));
    }

    public List<String> getList(String configKey) {
        return getList(configKey, "");
    }

    public boolean isDefenseEnabled(String defenseType) {
        String configKey = "gateway.defense." + defenseType + ".enabled";
        return getBoolean(configKey, true);
    }

    public int getRateLimitThreshold() {
        return getInt("gateway.defense.rate-limit.default-threshold", 30);
    }

    public int getBlacklistExpireSeconds() {
        return getInt("gateway.defense.blacklist.default-expire-seconds", 600);
    }

    public long getTrafficCacheExpireMs() {
        return getLong("gateway.cache.traffic-expire-ms", 3600000L);
    }

    public long getBlacklistCacheExpireMs() {
        return getLong("gateway.cache.blacklist-expire-ms", 600000L);
    }

    public long getCacheCleanupIntervalMs() {
        return getLong("gateway.cache.cleanup-interval-ms", 60000L);
    }

    public int getMaxRequestBodySize() {
        return getInt("gateway.request.max-body-size", 102400);
    }

    public long getAbnormalResponseThresholdMs() {
        return getLong("gateway.request.abnormal-response-threshold-ms", 3000L);
    }

    public List<String> getMaliciousUserAgents() {
        return getList("gateway.defense.malicious.user-agents", 
                "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
    }

    public List<String> getMaliciousUriPatterns() {
        return getList("gateway.defense.malicious.uri-patterns",
                "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
    }

    public long getTrafficPushBatchIntervalMs() {
        return getLong("traffic.push.batch-interval-ms", 5000L);
    }

    public boolean isTrafficPushEnabled() {
        return getBoolean("traffic.push.enabled", true);
    }

    public String getTrafficPushStrategy(String state) {
        return getString("traffic.push." + state.toLowerCase() + ".strategy", "realtime");
    }

    public int getDdosThreshold() {
        return getInt("ddos.threshold", 50);
    }

    public long getDdosDetectionWindowMs() {
        return getLong("ddos.detection.window-ms", 1000L);
    }

    public int getDdosRateLimitTriggerCount() {
        return getInt("ddos.rate-limit-trigger-count", 5);
    }

    public int getDdosRateLimitTriggerWindowSeconds() {
        return getInt("ddos.rate-limit-trigger-window-seconds", 30);
    }

    public int getStateNormalToSuspiciousThresholdRps() {
        return getInt("state.normal-to-suspicious.threshold-rps", 30);
    }

    public long getStateNormalToSuspiciousWindowMs() {
        return getLong("state.normal-to-suspicious.window-ms", 1000L);
    }

    public long getStateSuspiciousToAttackingDurationMs() {
        return getLong("state.suspicious-to-attacking.duration-ms", 5000L);
    }

    public int getStateSuspiciousToAttackingMinRequests() {
        return getInt("state.suspicious-to-attacking.min-requests", 50);
    }

    public int getStateSuspiciousToAttackingUriDiversityThreshold() {
        return getInt("state.suspicious-to-attacking.uri-diversity-threshold", 3);
    }

    public long getStateSuspiciousToNormalQuietDurationMs() {
        return getLong("state.suspicious-to-normal.quiet-duration-ms", 10000L);
    }

    public long getStateDefendedToCooldownQuietDurationMs() {
        return getLong("state.defended-to-cooldown.quiet-duration-ms", 30000L);
    }

    public long getStateCooldownBaseDurationMs() {
        return getLong("state.cooldown.base-duration-ms", 180000L);
    }

    public long getStateCooldownMaxDurationMs() {
        return getLong("state.cooldown.max-duration-ms", 600000L);
    }

    public double getStateCooldownAttackIntensityMultiplier() {
        String value = getString("state.cooldown.attack-intensity-multiplier", "0.5");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    public int getStateCooldownToAttackingThresholdRps() {
        return getInt("state.cooldown-to-attacking.threshold-rps", 3);
    }

    public boolean isCooldownDynamicEnabled() {
        return getBoolean("cooldown.dynamic.enabled", true);
    }

    public long getCooldownBaseDurationMs() {
        return getLong("cooldown.base-duration-ms", 180000L);
    }

    public long getCooldownMaxDurationMs() {
        return getLong("cooldown.max-duration-ms", 600000L);
    }

    public double getCooldownIntensityMultiplier() {
        String value = getString("cooldown.intensity-multiplier", "0.5");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    public double getCooldownHistoryMultiplier() {
        String value = getString("cooldown.history-multiplier", "0.2");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.2;
        }
    }

    public double getCooldownHistoryMaxMultiplier() {
        String value = getString("cooldown.history-max-multiplier", "2.0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 2.0;
        }
    }

    public int getConfidenceBaseScore() {
        return getInt("confidence.base-score", 0);
    }

    public int getConfidenceFrequencyMaxScore() {
        return getInt("confidence.frequency.max-score", 25);
    }

    public int getConfidencePerExceedScore() {
        return getInt("confidence.frequency.per-exceed-score", 5);
    }

    public int getConfidenceDiversityMaxScore() {
        return getInt("confidence.diversity.max-score", 20);
    }

    public int getConfidencePerUriScore() {
        return getInt("confidence.diversity.per-uri-score", 3);
    }

    public int getConfidencePersistenceMaxScore() {
        return getInt("confidence.persistence.max-score", 15);
    }

    public int getConfidencePer10sScore() {
        return getInt("confidence.persistence.per-10s-score", 3);
    }

    public int getConfidencePatternMaxScore() {
        return getInt("confidence.pattern.max-score", 10);
    }

    public int getConfidenceNormalBehaviorMaxDeduction() {
        return getInt("confidence.normal-behavior.max-deduction", 20);
    }

    public int getConfidenceNormalBehaviorNoHistoryDeduction() {
        return getInt("confidence.normal-behavior.no-history-deduction", 5);
    }

    public int getConfidenceNormalBehaviorNormalRequestsDeduction() {
        return getInt("confidence.normal-behavior.normal-requests-deduction", 15);
    }

    public String getConfidenceSmoothStrategy() {
        return getString("confidence.smooth.strategy", "ONLY_UP");
    }

    public double getConfidenceSmoothAlpha() {
        String value = getString("confidence.smooth.alpha", "0.4");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.4;
        }
    }

    public long getTrafficPushIntervalMs() {
        return getLong("traffic.push.interval-ms", 3000L);
    }

    public long getTrafficPushAggregateIntervalMs() {
        return getLong("traffic.push.aggregate-interval-ms", 5000L);
    }

    public int getTrafficSampleMaxPerUri() {
        return getInt("traffic.sample.max-per-uri", 3);
    }

    public int getTrafficSampleMaxTotal() {
        return getInt("traffic.sample.max-total", 20);
    }

    public int getTrafficAggregateUriPatternDepth() {
        return getInt("traffic.aggregate.uri-pattern-depth", 2);
    }

    public int getTrafficAggregateMaxUriGroups() {
        return getInt("traffic.aggregate.max-uri-groups", 50);
    }

    public int getTrafficPushRetryMaxCount() {
        return getInt("traffic.push.retry.max-count", 3);
    }

    public long getTrafficPushRetryDelayMs() {
        return getLong("traffic.push.retry.delay-ms", 1000L);
    }

    public int getTrafficPushRetryMaxQueueSize() {
        return getInt("traffic.push.retry.max-queue-size", 10000);
    }

    public int getTrafficPushMemoryMaxUsagePercent() {
        return getInt("traffic.push.memory.max-usage-percent", 80);
    }

    public int getTrafficPushMemoryForceFlushThreshold() {
        return getInt("traffic.push.memory.force-flush-threshold", 90);
    }

    public boolean isTrafficPushDegradationEnabled() {
        return getBoolean("traffic.push.degradation.enabled", true);
    }

    public int getTrafficPushDegradationLocalCacheSize() {
        return getInt("traffic.push.degradation.local-cache-size", 50000);
    }

    public long getTrafficPushDegradationHealthCheckIntervalMs() {
        return getLong("traffic.push.degradation.health-check-interval-ms", 30000L);
    }

    public int size() {
        return configCache.size();
    }

    public Map<String, String> getAllConfigs() {
        return Map.copyOf(configCache);
    }

    public void refresh() {
        logger.info("刷新网关配置缓存...");
        pullFromMonitorService();
    }

    public String getStats() {
        return String.format("网关配置缓存统计 - 总数:%d", configCache.size());
    }

    public boolean containsKey(String configKey) {
        return configCache.containsKey(configKey);
    }

    public long getStateSuspiciousTimeoutMs() {
        return getLong("state.suspicious.timeout-ms", 30000L);
    }

    public long getStateCooldownTimeoutMs() {
        return getLong("state.cooldown.timeout-ms", 600000L);
    }

    public long getSlidingWindowStepMs() {
        return getLong("state.normal-to-suspicious.slide-step-ms", 100L);
    }

    public long getStateSlowAttackDurationMs() {
        return getLong("state.slow-attack.duration-ms", 60000L);
    }

    public int getStateSlowAttackThresholdRps() {
        return getInt("state.slow-attack.threshold-rps", 5);
    }

    public int getStateGlobalAttackRelatedIpThreshold() {
        return getInt("state.global-attack.related-ip-threshold", 5);
    }

    public int getStateGlobalAttackNetworkMask() {
        return getInt("state.global-attack.network-mask", 24);
    }

    public long getTrafficPushIntervalLowMs() {
        return getLong("traffic.push.interval-low-ms", 10000L);
    }

    public boolean isTrafficSampleAbnormalPriority() {
        return getBoolean("traffic.sample.abnormal-priority", true);
    }

    public boolean isTrafficSampleDesensitizeEnabled() {
        return getBoolean("traffic.sample.desensitize-enabled", true);
    }

    public int getTrafficAggregateBatchThreshold() {
        return getInt("traffic.aggregate.batch-threshold", 10);
    }

    public int getTrafficQueueSingleIpCapacity() {
        return getInt("traffic.queue.single-ip-capacity", 50);
    }

    public int getTrafficQueueGlobalCapacity() {
        return getInt("traffic.queue.global-capacity", 1000);
    }

    public String getTrafficQueueOverflowStrategy() {
        return getString("traffic.queue.overflow-strategy", "DROP_OLDEST_SAMPLE");
    }

    public boolean isTrafficPushFallbackEnabled() {
        return getBoolean("traffic.push.fallback-enabled", true);
    }

    public boolean isBusinessPeakEnabled() {
        return getBoolean("traffic.business-peak.enabled", true);
    }

    public int getBusinessPeakThresholdMultiplier() {
        return getInt("traffic.business-peak.threshold-multiplier", 2);
    }

    public int getConfidenceHistoryMaxScore() {
        return getInt("confidence.history.max-score", 10);
    }

    public int getConfidenceHistoryNoAttackDeduction() {
        return getInt("confidence.history.no-attack-deduction", 5);
    }

    public int getConfidenceHistoryHasAttackScore() {
        return getInt("confidence.history.has-attack-score", 10);
    }

    public int getConfidenceHistoryNormalRateDeduction() {
        return getInt("confidence.history.normal-rate-deduction", 10);
    }

    public int getConfidenceSlowAttackMaxScore() {
        return getInt("confidence.slow-attack.max-score", 10);
    }

    public int getConfidenceSlowAttackPerMinuteScore() {
        return getInt("confidence.slow-attack.per-minute-score", 5);
    }

    public int getConfidenceGlobalAttackMaxScore() {
        return getInt("confidence.global-attack.max-score", 10);
    }

    public int getConfidenceGlobalAttackPerIpScore() {
        return getInt("confidence.global-attack.per-ip-score", 2);
    }

    public int getConfidencePatternPartialScore() {
        return getInt("confidence.pattern.partial-score", 5);
    }

    public boolean isConfidenceNoDecreaseEnabled() {
        return getBoolean("confidence.no-decrease.enabled", true);
    }

    public int getConfidenceMinValue() {
        return getInt("confidence.min-value", 0);
    }

    public long getSlowAttackDurationMs() {
        return getLong("state.slow-attack.duration-ms", 60000L);
    }

    public int getSlowAttackThresholdRps() {
        return getInt("state.slow-attack.threshold-rps", 5);
    }

    public int getGlobalAttackRelatedIpThreshold() {
        return getInt("state.global-attack.related-ip-threshold", 5);
    }

    public int getGlobalAttackNetworkMask() {
        return getInt("state.global-attack.network-mask", 24);
    }

    public boolean isManualResetLogRequired() {
        return getBoolean("state.manual-reset.log-required", true);
    }

    public boolean isManualResetOperatorRequired() {
        return getBoolean("state.manual-reset.operator-required", true);
    }

    public int getRateLimitPeakThreshold() {
        return getInt("gateway.defense.rate-limit.peak-threshold", 60);
    }

    public long getBanDurationBaseMs() {
        return getLong("gateway.defense.ban.duration-base-ms", 300000L);
    }

    public int getBanDurationMultiplier() {
        return getInt("gateway.defense.ban.duration-multiplier", 6);
    }

    public int getDdosSlowAttackThresholdRps() {
        return getInt("ddos.slow-attack.threshold-rps", 5);
    }

    public int getDdosGlobalAttackRelatedIpThreshold() {
        return getInt("ddos.global-attack.related-ip-threshold", 5);
    }

    public String getBusinessPeakTimeRanges() {
        return getString("traffic.business-peak.time-ranges", "09:00-12:00,14:00-18:00");
    }

    public List<String> getPushRetryIntervals() {
        return getList("traffic.push.retry-interval-ms", "500,1000,2000");
    }
}
