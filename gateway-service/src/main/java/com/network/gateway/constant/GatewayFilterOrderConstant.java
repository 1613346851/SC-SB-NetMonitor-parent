package com.network.gateway.constant;

/**
 * 网关过滤器优先级常量类
 * 定义各个过滤器的执行顺序，数值越小优先级越高
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayFilterOrderConstant {

    /**
     * 流量采集过滤器优先级（最高优先级）
     * 必须最先执行，确保采集到完整的请求信息
     */
    public static final int TRAFFIC_COLLECT_FILTER_ORDER = -100;

    /**
     * IP黑名单防御过滤器优先级
     */
    public static final int IP_BLACKLIST_DEFENSE_FILTER_ORDER = 0;

    /**
     * 防御状态检查过滤器优先级
     * 在黑名单过滤器之后，限流过滤器之前执行
     */
    public static final int DEFENSE_STATE_CHECK_FILTER_ORDER = 5;

    /**
     * 请求限流过滤器优先级
     */
    public static final int REQUEST_RATE_LIMIT_FILTER_ORDER = 10;

    /**
     * 恶意请求拦截过滤器优先级（最低防御优先级）
     */
    public static final int MALICIOUS_REQUEST_BLOCK_FILTER_ORDER = 20;

    /**
     * 攻击规则检测过滤器优先级
     * 在限流过滤器之后，恶意请求拦截过滤器之前执行
     */
    public static final int ATTACK_RULE_FILTER_ORDER = 15;

    private GatewayFilterOrderConstant() {
        // 私有构造函数，防止实例化
    }
}