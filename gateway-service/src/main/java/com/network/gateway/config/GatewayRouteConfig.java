package com.network.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义路由配置类
 * 可选配置类，用于动态路由配置（当前项目使用yml静态配置）
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Configuration
public class GatewayRouteConfig {

    /**
     * 自定义路由配置示例
     * 注意：当前项目主要使用application.yml配置路由
     * 此处仅作为动态路由的参考实现
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 靶场服务路由
                .route("target_service_route", r -> r
                        .path("/target/**")
                        .uri("http://localhost:9001")
                        .filter(f -> f.stripPrefix(0))
                )
                // 监控服务路由（如果需要的话）
                .route("monitor_service_route", r -> r
                        .path("/monitor/**")
                        .uri("http://localhost:9002")
                )
                // 健康检查路由
                .route("health_check_route", r -> r
                        .path("/health", "/actuator/**")
                        .uri("http://localhost:9000")  // 网关自身
                )
                .build();
    }
    */

    /**
     * 带权重的路由配置示例
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator weightedRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("weighted_route", r -> r
                        .host("**.weighted.example.com")
                        .filters(f -> f.weight("group1", 80))  // 80%权重
                        .uri("http://service1:8080")
                )
                .route("weighted_route_backup", r -> r
                        .host("**.weighted.example.com")
                        .filters(f -> f.weight("group1", 20))  // 20%权重
                        .uri("http://service2:8080")
                )
                .build();
    }
    */

    /**
     * 带重试机制的路由配置示例
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator retryRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("retry_route", r -> r
                        .path("/retry/**")
                        .filters(f -> f.retry(config -> config
                                .setRetries(3)
                                .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR)
                                .setMethods(HttpMethod.GET, HttpMethod.POST)
                                .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true)
                        ))
                        .uri("http://unstable-service:8080")
                )
                .build();
    }
    */

    /**
     * 带熔断器的路由配置示例
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator circuitBreakerRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("circuit_breaker_route", r -> r
                        .path("/cb/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("myCircuitBreaker")
                                .setFallbackUri("forward:/fallback")
                        ))
                        .uri("http://problematic-service:8080")
                )
                .build();
    }
    */

    /**
     * 带请求头修改的路由配置示例
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator headerModificationRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("header_mod_route", r -> r
                        .path("/api/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Version", "1.0")
                                .addResponseHeader("X-Response-Time", "#{new java.util.Date()}")
                                .removeRequestHeader("X-Sensitive-Header")
                        )
                        .uri("http://backend-service:8080")
                )
                .build();
    }
    */

    /**
     * 带路径重写的路由配置示例
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    /*
    @Bean
    public RouteLocator pathRewriteRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("path_rewrite_route", r -> r
                        .path("/legacy/{segment}")
                        .filters(f -> f
                                .rewritePath("/legacy/(?<segment>.*)", "/modern/${segment}")
                                .setPath("/{segment}/v2")
                        )
                        .uri("http://modern-service:8080")
                )
                .build();
    }
    */
}