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
        // 设置响应状态码
        response.setStatusCode(HttpStatus.FORBIDDEN);
        
        // 设置响应头
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.TEXT_HTML + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        
        // 构建响应体
        String responseBody = buildBlacklistHtml(blockedIp, eventId);
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer))
                .then(response.setComplete());
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
        // 设置响应状态码
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        
        // 设置响应头
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.APPLICATION_JSON + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.RETRY_AFTER, "1"); // 建议1秒后重试
        headers.add("X-RateLimit-Limit", String.valueOf(rateLimit));
        headers.add("X-RateLimit-Remaining", "0");
        
        // 构建响应体
        String responseBody = buildRateLimitJson(sourceIp, rateLimit);
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer))
                .then(response.setComplete());
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
        // 设置响应状态码
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        
        // 设置响应头
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, GatewayHttpConstant.ContentType.TEXT_HTML + ";charset=" + GatewayHttpConstant.Charset.UTF_8);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        // 构建响应体
        String responseBody = buildMaliciousRequestHtml(sourceIp, eventId, riskLevel);
        DataBuffer buffer = BUFFER_FACTORY.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer))
                .then(response.setComplete());
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
        
        return response.writeWith(Mono.just(buffer))
                .then(response.setComplete());
    }

    /**
     * 构建IP黑名单HTML页面
     *
     * @param blockedIp 被阻止的IP
     * @param eventId 事件ID
     * @return HTML内容
     */
    private static String buildBlacklistHtml(String blockedIp, String eventId) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="%s">
                <title>访问被拒绝</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #f8f9fa; }
                    .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 20px rgba(0,0,0,0.1); }
                    .error-icon { font-size: 60px; color: #dc3545; margin-bottom: 20px; }
                    h1 { color: #333; margin-bottom: 20px; }
                    p { color: #666; line-height: 1.6; margin: 10px 0; }
                    .info { background: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; border-radius: 5px; margin: 20px 0; }
                    .footer { margin-top: 30px; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="error-icon">🚫</div>
                    <h1>访问被拒绝</h1>
                    <p>您的IP地址 <strong>%s</strong> 已被加入黑名单。</p>
                    <p>此操作可能是由于检测到恶意活动或违反了服务条款。</p>
                    
                    <div class="info">
                        <strong>事件ID:</strong> %s<br>
                        <strong>如果您认为这是错误的，请联系系统管理员。</strong>
                    </div>
                    
                    <div class="footer">
                        Security Alert System | Event ID: %s
                    </div>
                </div>
            </body>
            </html>
            """, GatewayHttpConstant.Charset.UTF_8, blockedIp, eventId, eventId);
    }

    /**
     * 构建限流JSON响应
     *
     * @param sourceIp 源IP
     * @param rateLimit 限流阈值
     * @return JSON内容
     */
    private static String buildRateLimitJson(String sourceIp, int rateLimit) {
        return String.format("""
            {
                "error": "Too Many Requests",
                "message": "请求频率超过限制",
                "details": {
                    "source_ip": "%s",
                    "rate_limit": %d,
                    "retry_after": 1,
                    "description": "为了保护服务稳定性，您的请求已被临时限制"
                }
            }
            """, sourceIp, rateLimit);
    }

    /**
     * 构建恶意请求拦截HTML页面
     *
     * @param sourceIp 源IP
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     * @return HTML内容
     */
    private static String buildMaliciousRequestHtml(String sourceIp, String eventId, String riskLevel) {
        String riskDescription = getRiskDescription(riskLevel);
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="%s">
                <title>请求被拦截</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #fff3f3; }
                    .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 20px rgba(220,53,69,0.3); }
                    .warning-icon { font-size: 60px; color: #fd7e14; margin-bottom: 20px; }
                    h1 { color: #333; margin-bottom: 20px; }
                    p { color: #666; line-height: 1.6; margin: 10px 0; }
                    .risk-level { display: inline-block; padding: 5px 15px; border-radius: 15px; font-weight: bold; }
                    .risk-high { background: #f8d7da; color: #721c24; }
                    .risk-medium { background: #fff3cd; color: #856404; }
                    .info { background: #f8f9fa; border: 1px solid #dee2e6; padding: 15px; border-radius: 5px; margin: 20px 0; }
                    .footer { margin-top: 30px; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="warning-icon">⚠️</div>
                    <h1>请求被安全系统拦截</h1>
                    <p>检测到您的请求包含潜在的安全风险。</p>
                    <p>风险等级: <span class="risk-level %s">%s</span></p>
                    
                    <div class="info">
                        <strong>检测到的问题:</strong><br>
                        您的请求被识别为可能的恶意活动<br><br>
                        <strong>事件ID:</strong> %s<br>
                        <strong>源IP:</strong> %s
                    </div>
                    
                    <p>如需帮助，请联系系统管理员并提供上述事件ID。</p>
                    
                    <div class="footer">
                        Network Security System | Event ID: %s
                    </div>
                </div>
            </body>
            </html>
            """, GatewayHttpConstant.Charset.UTF_8, 
                getRiskClass(riskLevel), riskDescription, 
                eventId, sourceIp, eventId);
    }

    /**
     * 获取风险等级描述
     *
     * @param riskLevel 风险等级
     * @return 描述文本
     */
    private static String getRiskDescription(String riskLevel) {
        return switch (riskLevel.toUpperCase()) {
            case "HIGH" -> "高风险";
            case "CRITICAL" -> "严重风险";
            case "MEDIUM" -> "中等风险";
            case "LOW" -> "低风险";
            default -> "未知风险";
        };
    }

    /**
     * 获取风险等级CSS类
     *
     * @param riskLevel 风险等级
     * @return CSS类名
     */
    private static String getRiskClass(String riskLevel) {
        return switch (riskLevel.toUpperCase()) {
            case "HIGH", "CRITICAL" -> "risk-high";
            case "MEDIUM", "LOW" -> "risk-medium";
            default -> "risk-medium";
        };
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