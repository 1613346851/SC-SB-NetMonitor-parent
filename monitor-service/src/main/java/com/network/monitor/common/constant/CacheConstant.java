package com.network.monitor.common.constant;

/**
 * 缓存常量配置
 */
public class CacheConstant {

    /**
     * 攻击规则缓存 key 前缀
     */
    public static final String RULE_CACHE_PREFIX = "cache:rule:";

    /**
     * 漏洞信息缓存 key 前缀
     */
    public static final String VULNERABILITY_CACHE_PREFIX = "cache:vuln:";

    /**
     * 黑名单缓存 key 前缀
     */
    public static final String BLACKLIST_CACHE_PREFIX = "cache:blacklist:";

    /**
     * 统计数据缓存 key 前缀
     */
    public static final String STAT_CACHE_PREFIX = "cache:stat:";

    /**
     * 流量数据缓存 key 前缀
     */
    public static final String TRAFFIC_CACHE_PREFIX = "cache:traffic:";

    /**
     * 攻击记录缓存 key 前缀
     */
    public static final String ATTACK_CACHE_PREFIX = "cache:attack:";

    /**
     * 默认过期时间（分钟）
     */
    public static final long DEFAULT_EXPIRE_MINUTES = 30L;

    /**
     * 规则缓存永不过期
     */
    public static final long RULE_CACHE_NO_EXPIRE = -1L;

    /**
     * 黑名单默认过期时间（小时）
     */
    public static final long BLACKLIST_EXPIRE_HOURS = 24L;
}
