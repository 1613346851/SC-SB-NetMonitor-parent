package com.network.gateway.util;

import com.network.gateway.constant.GatewayHttpConstant;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 防御响应构建工具类
 * 构建各种防御场景下的响应对象
 * 
 * 设计原则：不向攻击者暴露安全系统的存在和工作方式
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class DefenseResponseUtil {

    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    /**
     * 构建IP黑名单响应
     *
     * @param response ServerHttpResponse对象
     * @param blockedIp 被阻止的IP
     * @param eventId 关联事件ID
     * @return Mono<Void>
     */
    public static Mono<Void> buildIpBlacklistResponse(ServerHttpResponse response, 
                                                     String blockedIp, String eventId) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String responseBody = "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}";
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构建请求限流响应
     *
     * @param response ServerHttpResponse对象
     * @param sourceIp 源IP
     * @param rateLimit 限流阈值
     * @return Mono<Void>
     */
    public static Mono<Void> buildRateLimitResponse(ServerHttpResponse response, 
                                                   String sourceIp, int rateLimit) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.RETRY_AFTER, "1");
        headers.add("X-RateLimit-Limit", String.valueOf(rateLimit));
        headers.add("X-RateLimit-Remaining", "0");
        
        String responseBody = "{\"error\":\"Too Many Requests\",\"message\":\"Request rate limit exceeded\"}";
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构建恶意请求拦截响应
     *
     * @param response ServerHttpResponse对象
     * @param sourceIp 源IP
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     * @return Mono<Void>
     */
    public static Mono<Void> buildMaliciousRequestResponse(ServerHttpResponse response,
                                                         String sourceIp, String eventId, String riskLevel) {
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String responseBody = "{\"error\":\"Bad Request\",\"message\":\"Invalid request\"}";
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构建通用错误响应
     *
     * @param response ServerHttpResponse对象
     * @param statusCode HTTP状态码
     * @param message 错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> buildErrorResponse(ServerHttpResponse response,
                                              HttpStatus statusCode, String message) {
        response.setStatusCode(statusCode);
        
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        
        String responseBody = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", 
                                          statusCode.getReasonPhrase(), message);
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构建403禁止访问响应（用于直接拉黑场景）
     *
     * @param response ServerHttpResponse对象
     * @param sourceIp 源IP
     * @param message 错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> buildForbiddenResponse(ServerHttpResponse response,
                                                   String sourceIp, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String responseBody = "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}";
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 记录防御响应构建日志
     *
     * @param defenseType 防御类型
     * @param sourceIp 源IP
     * @param eventId 事件ID
     * @return 日志信息
     */
    public static String buildDefenseLog(String defenseType, String sourceIp, String eventId) {
        return String.format("构建%s响应: IP[%s] 事件[%s]", defenseType, sourceIp, eventId);
    }
}
