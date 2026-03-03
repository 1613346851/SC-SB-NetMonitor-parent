package com.network.gateway.constant;

/**
 * 网关缓存常量类
 * 定义各种缓存的过期时间和键前缀
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayCacheConstant {

    /**
     * 流量临时缓存过期时间（毫秒）- 1小时
     */
    public static final long TRAFFIC_CACHE_EXPIRE_TIME = 60 * 60 * 1000L;

    /**
     * IP黑名单缓存过期时间（毫秒）- 10分钟
     */
    public static final long BLACKLIST_CACHE_EXPIRE_TIME = 10 * 60 * 1000L;

    /**
     * 请求限流缓存过期时间（毫秒）- 1秒（固定窗口）
     */
    public static final long RATE_LIMIT_CACHE_EXPIRE_TIME = 1000L;

    /**
     * 缓存清理任务执行间隔（毫秒）- 1分钟
     */
    public static final long CACHE_CLEANUP_INTERVAL = 60 * 1000L;

    /**
     * 流量缓存键前缀
     */
    public static final String TRAFFIC_CACHE_KEY_PREFIX = "traffic:";

    /**
     * IP黑名单缓存键前缀
     */
    public static final String BLACKLIST_CACHE_KEY_PREFIX = "blacklist:";

    /**
     * 请求限流缓存键前缀
     */
    public static final String RATE_LIMIT_CACHE_KEY_PREFIX = "ratelimit:";

    /**
     * 最大请求体大小限制（字节）- 100KB
     */
    public static final int MAX_REQUEST_BODY_SIZE = 100 * 1024;

    /**
     * 异常响应时间阈值（毫秒）- 3秒
     */
    public static final long ABNORMAL_RESPONSE_TIME_THRESHOLD = 3000L;

    /**
     * 请求限流阈值（次/秒）
     */
    public static final int RATE_LIMIT_THRESHOLD = 10;

    private GatewayCacheConstant() {
        // 私有构造函数，防止实例化
    }
}