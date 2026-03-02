package com.network.gateway.constant;

/**
 * 网关路由常量类
 * 定义网关的核心路由规则和路径前缀
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayRouteConstant {

    /**
     * 靶场服务路由前缀
     * 所有以/target/**开头的请求都将被路由到靶场服务
     */
    public static final String TARGET_SERVICE_ROUTE_PREFIX = "/target/**";

    /**
     * 靶场服务路由ID
     */
    public static final String TARGET_SERVICE_ROUTE_ID = "target_service_route";

    /**
     * 靶场服务URI（本地开发环境）
     * 实际部署时可根据环境变量或配置中心动态调整
     */
    public static final String TARGET_SERVICE_URI = "http://localhost:9001";

    /**
     * 健康检查路径（需要过滤的路径）
     */
    public static final String HEALTH_CHECK_PATH = "/actuator/health";

    /**
     * 网关管理端点路径前缀
     */
    public static final String ACTUATOR_PATH_PREFIX = "/actuator/";

    private GatewayRouteConstant() {
        // 私有构造函数，防止实例化
    }
}