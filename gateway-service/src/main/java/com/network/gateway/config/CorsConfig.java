package com.network.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * 跨域配置类
 * 解决前端页面发起的跨域请求问题
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Configuration
public class CorsConfig {

    /**
     * 配置跨域过滤器
     *
     * @return CorsWebFilter实例
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的源（域名）
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",           // 本地开发
                "http://127.0.0.1:*",          // 本地回环
                "https://*.example.com",       // 生产环境域名（示例）
                "http://*.internal.local"      // 内网域名（示例）
        ));
        
        // 允许的HTTP方法
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));
        
        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Forwarded-For",
                "X-Real-IP",
                "User-Agent",
                "Cache-Control",
                "X-RateLimit-*"  // 限流相关头部
        ));
        
        // 暴露给客户端的响应头
        config.setExposedHeaders(Arrays.asList(
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After"
        ));
        
        // 是否允许携带凭证（cookies等）
        config.setAllowCredentials(true);
        
        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);
        
        // 注册配置到URL路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);  // 对所有路径生效
        
        return new CorsWebFilter(source);
    }

    /**
     * 配置严格的跨域策略（生产环境推荐）
     *
     * @return 严格模式的CorsWebFilter
     */
    @Bean("strictCorsFilter")
    public CorsWebFilter strictCorsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 仅允许特定的生产域名
        config.setAllowedOrigins(Collections.singletonList("https://yourdomain.com"));
        
        // 限制HTTP方法
        config.setAllowedMethods(Arrays.asList("GET", "POST"));
        
        // 限制请求头
        config.setAllowedHeaders(Collections.singletonList("Authorization"));
        
        // 不允许携带凭证
        config.setAllowCredentials(false);
        
        // 短缓存时间
        config.setMaxAge(1800L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);  // 仅对API路径生效
        
        return new CorsWebFilter(source);
    }

    /**
     * 配置开发环境的宽松跨域策略
     *
     * @return 开发模式的CorsWebFilter
     */
    @Bean("devCorsFilter")
    public CorsWebFilter devCorsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有源（仅限开发环境）
        config.addAllowedOriginPattern("*");
        
        // 允许所有方法
        config.addAllowedMethod("*");
        
        // 允许所有头
        config.addAllowedHeader("*");
        
        // 允许携带凭证
        config.setAllowCredentials(true);
        
        // 短缓存时间
        config.setMaxAge(1800L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
}