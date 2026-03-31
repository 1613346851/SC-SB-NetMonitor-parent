package com.network.monitor.service.impl;

import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.service.GatewayConfigService;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class GatewayConfigServiceImpl implements GatewayConfigService {

    private static final String GATEWAY_CONFIG_PREFIX = "gateway.";

    private static final List<String> GATEWAY_CONFIG_KEYS = Arrays.asList(
        "gateway.defense.blacklist.enabled",
        "gateway.defense.rate-limit.enabled",
        "gateway.defense.malicious-request.enabled",
        "gateway.defense.rate-limit.default-threshold",
        "gateway.defense.rate-limit.window-size",
        "gateway.defense.blacklist.default-expire-seconds",
        "gateway.defense.malicious.user-agents",
        "gateway.defense.malicious.uri-patterns",
        "gateway.cache.traffic-expire-ms",
        "gateway.cache.blacklist-expire-ms",
        "gateway.cache.cleanup-interval-ms",
        "gateway.attack-state.cooldown-duration-ms",
        "gateway.attack-state.state-expire-ms",
        "gateway.request.max-body-size",
        "gateway.request.abnormal-response-threshold-ms",
        "gateway.defense.rate-limit.peak-threshold",
        "gateway.defense.ban.duration-base-ms",
        "gateway.defense.ban.duration-multiplier",
        "ddos.threshold",
        "ddos.detection.window-ms",
        "ddos.rate-limit-trigger-count",
        "ddos.rate-limit-trigger-window-seconds",
        "ddos.slow-attack.threshold-rps",
        "ddos.global-attack.related-ip-threshold",
        "state.normal-to-suspicious.threshold-rps",
        "state.normal-to-suspicious.window-ms",
        "state.normal-to-suspicious.slide-step-ms",
        "state.suspicious-to-attacking.duration-ms",
        "state.suspicious-to-attacking.min-requests",
        "state.suspicious-to-attacking.uri-diversity-threshold",
        "state.suspicious-to-normal.quiet-duration-ms",
        "state.suspicious.timeout-ms",
        "state.defended-to-cooldown.quiet-duration-ms",
        "state.cooldown.base-duration-ms",
        "state.cooldown.max-duration-ms",
        "state.cooldown.attack-intensity-multiplier",
        "state.cooldown.timeout-ms",
        "state.cooldown-to-attacking.threshold-rps",
        "state.slow-attack.duration-ms",
        "state.slow-attack.threshold-rps",
        "state.global-attack.related-ip-threshold",
        "state.global-attack.network-mask",
        "state.manual-reset.log-required",
        "state.manual-reset.operator-required",
        "cooldown.dynamic.enabled",
        "cooldown.base-duration-ms",
        "cooldown.max-duration-ms",
        "cooldown.intensity-multiplier",
        "cooldown.history-multiplier",
        "cooldown.history-max-multiplier",
        "confidence.base-score",
        "confidence.frequency.max-score",
        "confidence.frequency.per-exceed-score",
        "confidence.diversity.max-score",
        "confidence.diversity.per-uri-score",
        "confidence.persistence.max-score",
        "confidence.persistence.per-10s-score",
        "confidence.pattern.max-score",
        "confidence.pattern.partial-score",
        "confidence.normal-behavior.max-deduction",
        "confidence.normal-behavior.no-history-deduction",
        "confidence.normal-behavior.normal-requests-deduction",
        "confidence.smooth.strategy",
        "confidence.smooth.alpha",
        "confidence.history.max-score",
        "confidence.history.no-attack-deduction",
        "confidence.history.has-attack-score",
        "confidence.history.normal-rate-deduction",
        "confidence.slow-attack.max-score",
        "confidence.slow-attack.per-minute-score",
        "confidence.global-attack.max-score",
        "confidence.global-attack.per-ip-score",
        "confidence.no-decrease.enabled",
        "confidence.min-value",
        "confidence.blocked.rate-limit-score",
        "confidence.blocked.blacklist-score",
        "confidence.blocked.max-daily-score",
        "traffic.push.normal.strategy",
        "traffic.push.suspicious.strategy",
        "traffic.push.attacking.strategy",
        "traffic.push.defended.strategy",
        "traffic.push.cooldown.strategy",
        "traffic.push.batch-interval-ms",
        "traffic.push.sampling-rate",
        "traffic.push.enabled",
        "traffic.push.interval-ms",
        "traffic.push.interval-low-ms",
        "traffic.push.retry.max-count",
        "traffic.push.retry.delay-ms",
        "traffic.push.retry.max-queue-size",
        "traffic.push.retry-interval-ms",
        "traffic.push.memory.max-usage-percent",
        "traffic.push.memory.force-flush-threshold",
        "traffic.push.degradation.enabled",
        "traffic.push.degradation.local-cache-size",
        "traffic.push.degradation.health-check-interval-ms",
        "traffic.push.fallback-enabled",
        "traffic.sample.max-per-uri",
        "traffic.sample.max-total",
        "traffic.sample.abnormal-priority",
        "traffic.sample.desensitize-enabled",
        "traffic.aggregate.uri-pattern-depth",
        "traffic.aggregate.max-uri-groups",
        "traffic.aggregate.batch-threshold",
        "traffic.queue.single-ip-capacity",
        "traffic.queue.global-capacity",
        "traffic.queue.overflow-strategy",
        "traffic.business-peak.enabled",
        "traffic.business-peak.threshold-multiplier",
        "traffic.business-peak.time-ranges"
    );

    private static final Map<String, String> CONFIG_DESCRIPTIONS = new LinkedHashMap<String, String>() {{
        put("gateway.defense.blacklist.enabled", "黑名单防御开关");
        put("gateway.defense.rate-limit.enabled", "限流防御开关");
        put("gateway.defense.malicious-request.enabled", "恶意请求拦截开关");
        put("gateway.defense.rate-limit.default-threshold", "默认限流阈值(次/秒)");
        put("gateway.defense.rate-limit.window-size", "限流时间窗口(毫秒)");
        put("gateway.defense.blacklist.default-expire-seconds", "黑名单默认过期时间(秒)");
        put("gateway.defense.malicious.user-agents", "恶意User-Agent列表(逗号分隔)");
        put("gateway.defense.malicious.uri-patterns", "恶意URI模式列表(逗号分隔)");
        put("gateway.cache.traffic-expire-ms", "流量缓存过期时间(毫秒)");
        put("gateway.cache.blacklist-expire-ms", "黑名单缓存过期时间(毫秒)");
        put("gateway.cache.cleanup-interval-ms", "缓存清理间隔(毫秒)");
        put("gateway.attack-state.cooldown-duration-ms", "冷却持续时间(毫秒)");
        put("gateway.attack-state.state-expire-ms", "攻击状态过期时间(毫秒)");
        put("gateway.request.max-body-size", "最大请求体大小(字节)");
        put("gateway.request.abnormal-response-threshold-ms", "异常响应时间阈值(毫秒)");
        put("gateway.defense.rate-limit.peak-threshold", "业务高峰期限流阈值(次/秒)");
        put("gateway.defense.ban.duration-base-ms", "封禁基础时长(毫秒)");
        put("gateway.defense.ban.duration-multiplier", "重复违规封禁时长倍数");
        put("ddos.threshold", "DDoS检测阈值(次/秒)");
        put("ddos.detection.window-ms", "DDoS检测时间窗口(毫秒)");
        put("ddos.rate-limit-trigger-count", "连续限流触发封禁阈值(次)");
        put("ddos.rate-limit-trigger-window-seconds", "连续限流检测时间窗口(秒)");
        put("ddos.slow-attack.threshold-rps", "DDoS慢速攻击检测阈值");
        put("ddos.global-attack.related-ip-threshold", "DDoS分布式攻击关联IP阈值");
        put("state.normal-to-suspicious.threshold-rps", "NORMAL到SUSPICIOUS的RPS阈值(次/秒)");
        put("state.normal-to-suspicious.window-ms", "NORMAL到SUSPICIOUS的检测窗口(毫秒)");
        put("state.normal-to-suspicious.slide-step-ms", "滑动窗口步进(毫秒)");
        put("state.suspicious-to-attacking.duration-ms", "SUSPICIOUS持续多久转为ATTACKING(毫秒)");
        put("state.suspicious-to-attacking.min-requests", "SUSPICIOUS期间最小请求数");
        put("state.suspicious-to-attacking.uri-diversity-threshold", "URI多样性阈值(不同URI数量)");
        put("state.suspicious-to-normal.quiet-duration-ms", "SUSPICIOUS静止多久恢复NORMAL(毫秒)");
        put("state.suspicious.timeout-ms", "SUSPICIOUS状态超时时间(毫秒)");
        put("state.defended-to-cooldown.quiet-duration-ms", "DEFENDED静止多久进入COOLDOWN(毫秒)");
        put("state.cooldown.base-duration-ms", "COOLDOWN基础时长(毫秒)");
        put("state.cooldown.max-duration-ms", "COOLDOWN最大时长(毫秒)");
        put("state.cooldown.attack-intensity-multiplier", "攻击强度系数");
        put("state.cooldown.timeout-ms", "COOLDOWN状态超时时间(毫秒)");
        put("state.cooldown-to-attacking.threshold-rps", "COOLDOWN期间重新攻击的RPS阈值");
        put("state.slow-attack.duration-ms", "慢速攻击判定持续时间(毫秒)");
        put("state.slow-attack.threshold-rps", "慢速攻击RPS阈值");
        put("state.global-attack.related-ip-threshold", "分布式攻击关联IP数量阈值");
        put("state.global-attack.network-mask", "关联IP网络掩码");
        put("state.manual-reset.log-required", "人工重置是否必须记录日志");
        put("state.manual-reset.operator-required", "人工重置是否必须填写操作人");
        put("cooldown.dynamic.enabled", "是否启用动态冷却时长");
        put("cooldown.base-duration-ms", "冷却基础时长(毫秒)");
        put("cooldown.max-duration-ms", "冷却最大时长(毫秒)");
        put("cooldown.intensity-multiplier", "冷却攻击强度系数");
        put("cooldown.history-multiplier", "冷却历史系数");
        put("cooldown.history-max-multiplier", "冷却历史最大系数");
        put("confidence.base-score", "置信度基础分");
        put("confidence.frequency.max-score", "频率异常最高分");
        put("confidence.frequency.per-exceed-score", "每超过阈值1倍的得分");
        put("confidence.diversity.max-score", "多样性最高分");
        put("confidence.diversity.per-uri-score", "每个不同URI的得分");
        put("confidence.persistence.max-score", "持续时间最高分");
        put("confidence.persistence.per-10s-score", "每持续10秒的得分");
        put("confidence.pattern.max-score", "攻击模式匹配最高分");
        put("confidence.pattern.partial-score", "攻击模式部分匹配得分");
        put("confidence.normal-behavior.max-deduction", "正常行为最高抵扣");
        put("confidence.normal-behavior.no-history-deduction", "无历史攻击记录抵扣");
        put("confidence.normal-behavior.normal-requests-deduction", "历史正常请求多抵扣");
        put("confidence.smooth.strategy", "置信度平滑策略");
        put("confidence.smooth.alpha", "滑动平均系数");
        put("confidence.history.max-score", "历史行为最高分");
        put("confidence.history.no-attack-deduction", "无历史攻击记录抵扣");
        put("confidence.history.has-attack-score", "历史有攻击记录得分");
        put("confidence.history.normal-rate-deduction", "正常请求比例抵扣");
        put("confidence.slow-attack.max-score", "慢速攻击最高分");
        put("confidence.slow-attack.per-minute-score", "慢速攻击每分钟得分");
        put("confidence.global-attack.max-score", "分布式攻击最高分");
        put("confidence.global-attack.per-ip-score", "每个关联IP得分");
        put("confidence.no-decrease.enabled", "是否启用置信度只升不降策略");
        put("confidence.min-value", "置信度最小值");
        put("confidence.blocked.rate-limit-score", "限流拦截置信度加分");
        put("confidence.blocked.blacklist-score", "黑名单拦截置信度加分");
        put("confidence.blocked.max-daily-score", "每日拦截最高加分");
        put("traffic.push.normal.strategy", "NORMAL状态推送策略");
        put("traffic.push.suspicious.strategy", "SUSPICIOUS状态推送策略");
        put("traffic.push.attacking.strategy", "ATTACKING状态推送策略");
        put("traffic.push.defended.strategy", "DEFENDED状态推送策略");
        put("traffic.push.cooldown.strategy", "COOLDOWN状态推送策略");
        put("traffic.push.batch-interval-ms", "批量推送间隔(毫秒)");
        put("traffic.push.sampling-rate", "采样推送比例(1/N)");
        put("traffic.push.enabled", "是否启用流量推送");
        put("traffic.push.interval-ms", "流量推送周期(毫秒)");
        put("traffic.push.interval-low-ms", "低谷期流量推送间隔(毫秒)");
        put("traffic.push.retry.max-count", "推送失败最大重试次数");
        put("traffic.push.retry.delay-ms", "重试延迟基础时间(毫秒)");
        put("traffic.push.retry.max-queue-size", "重试队列最大大小");
        put("traffic.push.retry-interval-ms", "推送重试间隔序列(毫秒)");
        put("traffic.push.memory.max-usage-percent", "内存使用上限百分比");
        put("traffic.push.memory.force-flush-threshold", "强制推送内存阈值百分比");
        put("traffic.push.degradation.enabled", "是否启用降级模式");
        put("traffic.push.degradation.local-cache-size", "降级模式本地缓存大小");
        put("traffic.push.degradation.health-check-interval-ms", "下游服务健康检查间隔(毫秒)");
        put("traffic.push.fallback-enabled", "是否启用推送失败降级");
        put("traffic.sample.max-per-uri", "每个URI模式保留的最大样本数");
        put("traffic.sample.max-total", "单次推送保留的最大样本总数");
        put("traffic.sample.abnormal-priority", "是否优先保留异常样本");
        put("traffic.sample.desensitize-enabled", "是否启用样本脱敏");
        put("traffic.aggregate.uri-pattern-depth", "URI模式聚合深度(路径段数)");
        put("traffic.aggregate.max-uri-groups", "单次推送最大URI分组数");
        put("traffic.aggregate.batch-threshold", "批量聚合推送阈值");
        put("traffic.queue.single-ip-capacity", "单个IP流量队列容量上限");
        put("traffic.queue.global-capacity", "全局流量队列容量上限");
        put("traffic.queue.overflow-strategy", "队列溢出策略");
        put("traffic.business-peak.enabled", "是否启用业务高峰模式");
        put("traffic.business-peak.threshold-multiplier", "业务高峰期RPS阈值倍数");
        put("traffic.business-peak.time-ranges", "业务高峰时段");
    }};

    private static final Map<String, String> DEFAULT_CONFIG_VALUES = new HashMap<String, String>() {{
        put("gateway.defense.blacklist.enabled", "true");
        put("gateway.defense.rate-limit.enabled", "true");
        put("gateway.defense.malicious-request.enabled", "true");
        put("gateway.defense.rate-limit.default-threshold", "30");
        put("gateway.defense.rate-limit.window-size", "1000");
        put("gateway.defense.blacklist.default-expire-seconds", "600");
        put("gateway.defense.malicious.user-agents", "sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa");
        put("gateway.defense.malicious.uri-patterns", "/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download");
        put("gateway.cache.traffic-expire-ms", "3600000");
        put("gateway.cache.blacklist-expire-ms", "600000");
        put("gateway.cache.cleanup-interval-ms", "60000");
        put("gateway.attack-state.cooldown-duration-ms", "300000");
        put("gateway.attack-state.state-expire-ms", "600000");
        put("gateway.request.max-body-size", "102400");
        put("gateway.request.abnormal-response-threshold-ms", "3000");
        put("gateway.defense.rate-limit.peak-threshold", "60");
        put("gateway.defense.ban.duration-base-ms", "300000");
        put("gateway.defense.ban.duration-multiplier", "6");
        put("ddos.threshold", "50");
        put("ddos.detection.window-ms", "1000");
        put("ddos.rate-limit-trigger-count", "5");
        put("ddos.rate-limit-trigger-window-seconds", "30");
        put("ddos.slow-attack.threshold-rps", "5");
        put("ddos.global-attack.related-ip-threshold", "5");
        put("state.normal-to-suspicious.threshold-rps", "30");
        put("state.normal-to-suspicious.window-ms", "1000");
        put("state.normal-to-suspicious.slide-step-ms", "100");
        put("state.suspicious-to-attacking.duration-ms", "5000");
        put("state.suspicious-to-attacking.min-requests", "50");
        put("state.suspicious-to-attacking.uri-diversity-threshold", "3");
        put("state.suspicious-to-normal.quiet-duration-ms", "10000");
        put("state.suspicious.timeout-ms", "30000");
        put("state.defended-to-cooldown.quiet-duration-ms", "30000");
        put("state.cooldown.base-duration-ms", "180000");
        put("state.cooldown.max-duration-ms", "600000");
        put("state.cooldown.attack-intensity-multiplier", "0.5");
        put("state.cooldown.timeout-ms", "600000");
        put("state.cooldown-to-attacking.threshold-rps", "20");
        put("state.slow-attack.duration-ms", "60000");
        put("state.slow-attack.threshold-rps", "5");
        put("state.global-attack.related-ip-threshold", "5");
        put("state.global-attack.network-mask", "24");
        put("state.manual-reset.log-required", "true");
        put("state.manual-reset.operator-required", "true");
        put("cooldown.dynamic.enabled", "true");
        put("cooldown.base-duration-ms", "180000");
        put("cooldown.max-duration-ms", "600000");
        put("cooldown.intensity-multiplier", "0.5");
        put("cooldown.history-multiplier", "0.2");
        put("cooldown.history-max-multiplier", "2.0");
        put("confidence.base-score", "30");
        put("confidence.frequency.max-score", "25");
        put("confidence.frequency.per-exceed-score", "5");
        put("confidence.diversity.max-score", "20");
        put("confidence.diversity.per-uri-score", "3");
        put("confidence.persistence.max-score", "15");
        put("confidence.persistence.per-10s-score", "3");
        put("confidence.pattern.max-score", "10");
        put("confidence.pattern.partial-score", "5");
        put("confidence.normal-behavior.max-deduction", "20");
        put("confidence.normal-behavior.no-history-deduction", "5");
        put("confidence.normal-behavior.normal-requests-deduction", "15");
        put("confidence.smooth.strategy", "ONLY_UP");
        put("confidence.smooth.alpha", "0.4");
        put("confidence.history.max-score", "10");
        put("confidence.history.no-attack-deduction", "5");
        put("confidence.history.has-attack-score", "10");
        put("confidence.history.normal-rate-deduction", "10");
        put("confidence.slow-attack.max-score", "10");
        put("confidence.slow-attack.per-minute-score", "5");
        put("confidence.global-attack.max-score", "10");
        put("confidence.global-attack.per-ip-score", "2");
        put("confidence.no-decrease.enabled", "true");
        put("confidence.min-value", "10");
        put("confidence.blocked.rate-limit-score", "3");
        put("confidence.blocked.blacklist-score", "5");
        put("confidence.blocked.max-daily-score", "30");
        put("traffic.push.normal.strategy", "realtime");
        put("traffic.push.suspicious.strategy", "sampling");
        put("traffic.push.attacking.strategy", "batch");
        put("traffic.push.defended.strategy", "skip");
        put("traffic.push.cooldown.strategy", "sampling");
        put("traffic.push.batch-interval-ms", "5000");
        put("traffic.push.sampling-rate", "10");
        put("traffic.push.enabled", "true");
        put("traffic.push.interval-ms", "3000");
        put("traffic.push.interval-low-ms", "10000");
        put("traffic.push.retry.max-count", "3");
        put("traffic.push.retry.delay-ms", "1000");
        put("traffic.push.retry.max-queue-size", "10000");
        put("traffic.push.retry-interval-ms", "500,1000,2000");
        put("traffic.push.memory.max-usage-percent", "80");
        put("traffic.push.memory.force-flush-threshold", "90");
        put("traffic.push.degradation.enabled", "true");
        put("traffic.push.degradation.local-cache-size", "50000");
        put("traffic.push.degradation.health-check-interval-ms", "30000");
        put("traffic.push.fallback-enabled", "true");
        put("traffic.sample.max-per-uri", "3");
        put("traffic.sample.max-total", "20");
        put("traffic.sample.abnormal-priority", "true");
        put("traffic.sample.desensitize-enabled", "true");
        put("traffic.aggregate.uri-pattern-depth", "2");
        put("traffic.aggregate.max-uri-groups", "50");
        put("traffic.aggregate.batch-threshold", "10");
        put("traffic.queue.single-ip-capacity", "50");
        put("traffic.queue.global-capacity", "1000");
        put("traffic.queue.overflow-strategy", "DROP_OLDEST_SAMPLE");
        put("traffic.business-peak.enabled", "true");
        put("traffic.business-peak.threshold-multiplier", "2");
        put("traffic.business-peak.time-ranges", "09:00-12:00,14:00-18:00");
    }};

    @Autowired
    private SysConfigCache sysConfigCache;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Override
    public Map<String, Object> getAllGatewayConfigs() {
        Map<String, Object> configs = new LinkedHashMap<>();

        for (String key : GATEWAY_CONFIG_KEYS) {
            String value = sysConfigCache.getConfigValue(key);
            if (value != null) {
                configs.put(key, value);
            } else {
                String defaultValue = DEFAULT_CONFIG_VALUES.get(key);
                if (defaultValue != null) {
                    configs.put(key, defaultValue);
                }
            }
        }

        log.debug("获取所有网关配置，共{}项", configs.size());
        return configs;
    }

    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        String value = sysConfigCache.getConfigValue(configKey);
        if (value != null) {
            return value;
        }
        return DEFAULT_CONFIG_VALUES.getOrDefault(configKey, defaultValue);
    }

    @Override
    public int getIntConfigValue(String configKey, int defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析为整数失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLongConfigValue(String configKey, long defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("配置值解析为长整数失败：configKey={}, value={}, 使用默认值{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanConfigValue(String configKey, boolean defaultValue) {
        String value = getConfigValue(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    @Override
    public List<String> getListConfigValue(String configKey, String defaultValue) {
        String value = getConfigValue(configKey, defaultValue);
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split(","));
    }

    @Override
    public void updateConfig(String configKey, String configValue) {
        if (!configKey.startsWith(GATEWAY_CONFIG_PREFIX)) {
            throw new IllegalArgumentException("非网关配置项: " + configKey);
        }

        sysConfigService.updateConfigValue(configKey, configValue);
        sysConfigCache.updateConfig(configKey, configValue);

        boolean pushSuccess = pushConfigToGateway(configKey, configValue);
        if (pushSuccess) {
            log.info("更新网关配置成功并已推送：configKey={}, configValue={}", configKey, configValue);
        } else {
            log.warn("更新网关配置成功但推送失败：configKey={}, configValue={}", configKey, configValue);
        }
    }

    @Override
    public boolean pushConfigToGateway(String configKey, String configValue) {
        try {
            return gatewayApiClient.pushConfigToGateway(configKey, configValue);
        } catch (Exception e) {
            log.error("推送配置到网关失败: key={}, value={}", configKey, configValue, e);
            return false;
        }
    }

    @Override
    public boolean pushAllConfigsToGateway() {
        try {
            Map<String, Object> configs = getAllGatewayConfigs();
            return gatewayApiClient.pushConfigsToGateway(configs);
        } catch (Exception e) {
            log.error("推送所有配置到网关失败", e);
            return false;
        }
    }

    @Override
    public void refreshGatewayConfigCache() {
        sysConfigCache.refresh();
        boolean pushSuccess = pushAllConfigsToGateway();
        if (pushSuccess) {
            log.info("刷新网关配置缓存成功并已推送所有配置");
        } else {
            log.warn("刷新网关配置缓存成功但推送失败");
        }
    }

    @Override
    public List<Map<String, Object>> getGatewayConfigList() {
        List<Map<String, Object>> configList = new ArrayList<>();

        for (String key : GATEWAY_CONFIG_KEYS) {
            Map<String, Object> configItem = new LinkedHashMap<>();
            configItem.put("configKey", key);
            configItem.put("configValue", getConfigValue(key, ""));
            configItem.put("description", CONFIG_DESCRIPTIONS.getOrDefault(key, ""));
            configItem.put("defaultValue", DEFAULT_CONFIG_VALUES.getOrDefault(key, ""));
            configList.add(configItem);
        }

        return configList;
    }

    @Override
    public int getGatewayConfigCount() {
        return GATEWAY_CONFIG_KEYS.size();
    }
}
