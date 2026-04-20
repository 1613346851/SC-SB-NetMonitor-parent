package com.network.gateway.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * 防御响应构建工具类
 * 构建各种防御场景下的响应对象
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class DefenseResponseUtil {

    /**
     * 构建IP黑名单响应（仅返回状态码，无响应体）
     */
    public static Mono<Void> buildIpBlacklistResponse(ServerHttpResponse response,
                                                     String blockedIp, String eventId) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        return response.setComplete();
    }

    /**
     * 构建请求限流响应（仅返回状态码，无响应体）
     */
    public static Mono<Void> buildRateLimitResponse(ServerHttpResponse response,
                                                   String sourceIp, int rateLimit) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "1");
        headers.add("X-RateLimit-Limit", String.valueOf(rateLimit));
        headers.add("X-RateLimit-Remaining", "0");
        return response.setComplete();
    }

    /**
     * 构建恶意请求拦截响应（仅返回状态码，无响应体）
     */
    public static Mono<Void> buildMaliciousRequestResponse(ServerHttpResponse response,
                                                         String sourceIp, String eventId, String riskLevel) {
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        return response.setComplete();
    }

    /**
     * 构建通用错误响应（仅返回状态码，无响应体）
     */
    public static Mono<Void> buildErrorResponse(ServerHttpResponse response,
                                              HttpStatus statusCode, String message) {
        response.setStatusCode(statusCode);
        return response.setComplete();
    }

    /**
     * 构建403禁止访问响应（仅返回状态码，无响应体）
     */
    public static Mono<Void> buildForbiddenResponse(ServerHttpResponse response,
                                                   String sourceIp, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        return response.setComplete();
    }

    /**
     * 记录防御响应构建日志
     */
    public static String buildDefenseLog(String defenseType, String sourceIp, String eventId) {
        return String.format("构建%s响应: IP[%s] 事件[%s]", defenseType, sourceIp, eventId);
    }
}
