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

    private static String buildErrorPageHtml(int statusCode, String errorTitle, String errorMessage, String icon) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>" + errorTitle + "</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            min-height: 100vh;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        .error-container {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);\n" +
                "            padding: 60px 80px;\n" +
                "            text-align: center;\n" +
                "            max-width: 500px;\n" +
                "            width: 100%;\n" +
                "        }\n" +
                "        .error-icon {\n" +
                "            font-size: 72px;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .error-code {\n" +
                "            font-size: 120px;\n" +
                "            font-weight: 700;\n" +
                "            color: #e74c3c;\n" +
                "            line-height: 1;\n" +
                "            margin-bottom: 16px;\n" +
                "            text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);\n" +
                "        }\n" +
                "        .error-code.code-429 {\n" +
                "            color: #f39c12;\n" +
                "        }\n" +
                "        .error-code.code-400 {\n" +
                "            color: #9b59b6;\n" +
                "        }\n" +
                "        .error-title {\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 600;\n" +
                "            color: #2c3e50;\n" +
                "            margin-bottom: 16px;\n" +
                "        }\n" +
                "        .error-message {\n" +
                "            font-size: 16px;\n" +
                "            color: #7f8c8d;\n" +
                "            margin-bottom: 32px;\n" +
                "            line-height: 1.6;\n" +
                "        }\n" +
                "        .error-actions {\n" +
                "            display: flex;\n" +
                "            gap: 16px;\n" +
                "            justify-content: center;\n" +
                "            flex-wrap: wrap;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            padding: 12px 28px;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 14px;\n" +
                "            font-weight: 500;\n" +
                "            text-decoration: none;\n" +
                "            transition: all 0.3s ease;\n" +
                "            cursor: pointer;\n" +
                "            border: none;\n" +
                "        }\n" +
                "        .btn-primary {\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .btn-primary:hover {\n" +
                "            transform: translateY(-2px);\n" +
                "            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);\n" +
                "        }\n" +
                "        .btn-secondary {\n" +
                "            background: #ecf0f1;\n" +
                "            color: #2c3e50;\n" +
                "        }\n" +
                "        .btn-secondary:hover {\n" +
                "            background: #bdc3c7;\n" +
                "        }\n" +
                "        .divider {\n" +
                "            height: 1px;\n" +
                "            background: #ecf0f1;\n" +
                "            margin: 24px 0;\n" +
                "        }\n" +
                "        .help-text {\n" +
                "            font-size: 13px;\n" +
                "            color: #95a5a6;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"error-container\">\n" +
                "        <div class=\"error-icon\">" + icon + "</div>\n" +
                "        <div class=\"error-code code-" + statusCode + "\">" + statusCode + "</div>\n" +
                "        <h1 class=\"error-title\">" + errorTitle + "</h1>\n" +
                "        <p class=\"error-message\">" + errorMessage + "</p>\n" +
                "        <div class=\"error-actions\">\n" +
                "            <a href=\"javascript:history.back()\" class=\"btn btn-secondary\">返回上一页</a>\n" +
                "            <a href=\"/\" class=\"btn btn-primary\">返回首页</a>\n" +
                "        </div>\n" +
                "        <div class=\"divider\"></div>\n" +
                "        <p class=\"help-text\">如果您认为这是一个错误，请联系系统管理员</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

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
        headers.add(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String html = buildErrorPageHtml(403, "访问被拒绝", 
                "您的访问请求已被拒绝。<br>如有疑问，请联系系统管理员。", "\uD83D\uDEAB");
        DataBuffer buffer = BUFFER_FACTORY.wrap(html.getBytes(StandardCharsets.UTF_8));
        
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
        headers.add(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        headers.add(HttpHeaders.RETRY_AFTER, "1");
        headers.add("X-RateLimit-Limit", String.valueOf(rateLimit));
        headers.add("X-RateLimit-Remaining", "0");
        
        String html = buildErrorPageHtml(429, "请求过于频繁", 
                "您的请求频率过高，请稍后再试。<br>系统将在几秒后自动恢复访问。", "⏱\uFE0F");
        DataBuffer buffer = BUFFER_FACTORY.wrap(html.getBytes(StandardCharsets.UTF_8));
        
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
        headers.add(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String html = buildErrorPageHtml(400, "请求无效", 
                "您的请求包含无效内容，无法被服务器处理。<br>请检查您的请求后重试。", "⚠\uFE0F");
        DataBuffer buffer = BUFFER_FACTORY.wrap(html.getBytes(StandardCharsets.UTF_8));
        
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
        headers.add(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        
        String icon = statusCode.value() >= 500 ? "\uD83D\uDEA8" : "❌";
        String title = statusCode.getReasonPhrase();
        String html = buildErrorPageHtml(statusCode.value(), title, message, icon);
        DataBuffer buffer = BUFFER_FACTORY.wrap(html.getBytes(StandardCharsets.UTF_8));
        
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
        headers.add(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache,no-store,must-revalidate");
        
        String html = buildErrorPageHtml(403, "访问被拒绝", 
                "您的访问请求已被拒绝。<br>如有疑问，请联系系统管理员。", "\uD83D\uDEAB");
        DataBuffer buffer = BUFFER_FACTORY.wrap(html.getBytes(StandardCharsets.UTF_8));
        
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
