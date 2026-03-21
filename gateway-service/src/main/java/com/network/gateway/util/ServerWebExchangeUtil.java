package com.network.gateway.util;

import com.network.gateway.bo.RawTrafficBO;
import com.network.gateway.constant.GatewayHttpConstant;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ServerWebExchange工具类
 * 提取请求信息的核心工具类
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class ServerWebExchangeUtil {

    /**
     * 从ServerWebExchange中提取完整的请求信息
     *
     * @param exchange ServerWebExchange对象
     * @return RawTrafficBO 原始流量业务对象
     */
    public static RawTrafficBO extractTrafficInfo(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 生成请求 ID
        String requestId = generateRequestId();
        
        // 提取源 IP
        String sourceIp = extractSourceIp(request);
        
        // 提取目标 IP
        String targetIp = extractTargetIp(request);
        
        // 提取基本请求信息
        String method = request.getMethodValue();
        String uri = request.getURI().getPath();
        
        // 提取查询参数
        Map<String, String> queryParams = extractQueryParams(request);
        String rawQueryParams = request.getURI().getQuery();
        
        // 提取请求头
        Map<String, String> headers = extractHeaders(request);
        
        // 提取用户代理
        String userAgent = extractUserAgent(headers);
        
        // 提取端口信息
        Integer sourcePort = extractSourcePort(request);
        Integer targetPort = extractTargetPort(request);
        
        // 提取协议类型
        String protocol = extractProtocol(request);
        
        // 提取内容类型
        String contentType = extractContentType(request);
        
        // 创建原始流量对象
        RawTrafficBO trafficBO = new RawTrafficBO(
                requestId, sourceIp, method, uri, System.currentTimeMillis()
        );
        
        trafficBO.setTargetIp(targetIp);
        trafficBO.setQueryParams(queryParams);
        trafficBO.setRawQueryParams(rawQueryParams);
        trafficBO.setHeaders(headers);
        trafficBO.setUserAgent(userAgent);
        trafficBO.setSourcePort(sourcePort);
        trafficBO.setTargetPort(targetPort);
        trafficBO.setProtocol(protocol);
        trafficBO.setContentType(contentType);
        
        return trafficBO;
    }

    /**
     * 提取源IP地址
     *
     * @param request ServerHttpRequest对象
     * @return 源IP地址
     */
    public static String extractSourceIp(ServerHttpRequest request) {
        String ip = doExtractSourceIp(request);
        return IpNormalizeUtil.normalize(ip);
    }

    /**
     * 执行源IP提取（内部方法）
     *
     * @param request ServerHttpRequest对象
     * @return 源IP地址
     */
    private static String doExtractSourceIp(ServerHttpRequest request) {
        // 优先从X-Forwarded-For头部获取
        String xForwardedFor = request.getHeaders().getFirst(GatewayHttpConstant.Header.X_FORWARDED_FOR);
        if (StringUtils.hasText(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // 从X-Real-IP头部获取
        String xRealIp = request.getHeaders().getFirst(GatewayHttpConstant.Header.X_REAL_IP);
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        // 从远程地址获取
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        // 如果都获取不到，返回unknown
        return "unknown";
    }

    /**
     * 提取目标IP地址
     *
     * @param request ServerHttpRequest对象
     * @return 目标IP地址
     */
    public static String extractTargetIp(ServerHttpRequest request) {
        // 从X-Forwarded-Host头部获取
        String xForwardedHost = request.getHeaders().getFirst(GatewayHttpConstant.Header.X_FORWARDED_HOST);
        if (StringUtils.hasText(xForwardedHost)) {
            return xForwardedHost.trim();
        }

        // 从Host头部获取
        String host = request.getHeaders().getFirst(GatewayHttpConstant.Header.CONTENT_LENGTH);
        if (StringUtils.hasText(host)) {
            return host.trim();
        }

        // 从本地地址获取
        InetSocketAddress localAddress = request.getLocalAddress();
        if (localAddress != null) {
            return localAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 提取查询参数
     *
     * @param request ServerHttpRequest对象
     * @return 查询参数Map
     */
    public static Map<String, String> extractQueryParams(ServerHttpRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getQueryParams().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                params.put(key, values.get(0)); // 取第一个值
            }
        });
        return params;
    }

    /**
     * 提取请求头信息
     *
     * @param request ServerHttpRequest对象
     * @return 请求头Map
     */
    public static Map<String, String> extractHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, String.join(",", values)); // 多个值用逗号连接
            }
        });
        return headers;
    }

    /**
     * 提取用户代理信息
     *
     * @param headers 请求头Map
     * @return 用户代理字符串
     */
    public static String extractUserAgent(Map<String, String> headers) {
        return headers.getOrDefault(GatewayHttpConstant.Header.USER_AGENT, "unknown");
    }

    /**
     * 提取请求体内容（注意：只能读取一次）
     *
     * @param exchange ServerWebExchange对象
     * @return 请求体内容
     */
    public static String extractRequestBody(ServerWebExchange exchange) {
        // 注意：请求体只能读取一次，在实际使用中需要特别处理
        // 这里提供一个示例实现，实际应用中可能需要缓存机制
        return ""; // 简化处理，实际项目中需要更复杂的实现
    }

    /**
     * 生成唯一的请求ID
     *
     * @return 请求ID
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建完整的请求URL
     *
     * @param request ServerHttpRequest对象
     * @return 完整URL
     */
    public static String buildFullUrl(ServerHttpRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getURI().getScheme()).append("://");
        url.append(request.getURI().getAuthority());
        url.append(request.getURI().getPath());
        
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            url.append("?").append(query);
        }
        
        return url.toString();
    }

    /**
     * 检查是否为健康检查请求
     *
     * @param exchange ServerWebExchange对象
     * @return true表示是健康检查
     */
    public static boolean isHealthCheck(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.contains("/actuator/health") || path.equals("/health");
    }

    /**
     * 检查是否为管理端点请求
     *
     * @param exchange ServerWebExchange对象
     * @return true表示是管理端点
     */
    public static boolean isManagementEndpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith("/actuator/");
    }

    /**
     * 获取请求内容类型
     *
     * @param exchange ServerWebExchange对象
     * @return 内容类型
     */
    public static String getContentType(ServerWebExchange exchange) {
        List<String> contentTypeHeaders = exchange.getRequest().getHeaders().get(GatewayHttpConstant.Header.CONTENT_TYPE);
        if (contentTypeHeaders != null && !contentTypeHeaders.isEmpty()) {
            return contentTypeHeaders.get(0);
        }
        return "unknown";
    }

    /**
     * 获取请求内容长度
     *
     * @param exchange ServerWebExchange 对象
     * @return 内容长度
     */
    public static Long getContentLength(ServerWebExchange exchange) {
        List<String> contentLengthHeaders = exchange.getRequest().getHeaders().get(GatewayHttpConstant.Header.CONTENT_LENGTH);
        if (contentLengthHeaders != null && !contentLengthHeaders.isEmpty()) {
            try {
                return Long.parseLong(contentLengthHeaders.get(0));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 提取源端口
     *
     * @param request ServerHttpRequest 对象
     * @return 源端口
     */
    public static Integer extractSourcePort(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getPort();
        }
        return 0;
    }

    /**
     * 提取目标端口
     *
     * @param request ServerHttpRequest 对象
     * @return 目标端口
     */
    public static Integer extractTargetPort(ServerHttpRequest request) {
        InetSocketAddress localAddress = request.getLocalAddress();
        if (localAddress != null) {
            return localAddress.getPort();
        }
        return 0;
    }

    /**
     * 提取协议类型
     *
     * @param request ServerHttpRequest 对象
     * @return 协议类型
     */
    public static String extractProtocol(ServerHttpRequest request) {
        // 从请求头中获取协议版本信息
        String protocol = request.getHeaders().getFirst("X-Forwarded-Proto");
        if (protocol != null) {
            return protocol.toUpperCase() + "/1.1";
        }
        
        // 根据是否为 HTTPS 判断
        String scheme = request.getURI().getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return "HTTPS/1.1";
        }
        
        // 默认为 HTTP/1.1
        return "HTTP/1.1";
    }

    /**
     * 提取内容类型
     *
     * @param request ServerHttpRequest 对象
     * @return 内容类型
     */
    public static String extractContentType(ServerHttpRequest request) {
        String contentType = request.getHeaders().getFirst(GatewayHttpConstant.Header.CONTENT_TYPE);
        return contentType != null ? contentType : "unknown";
    }

    /**
     * 检查是否为静态资源请求
     *
     * @param exchange ServerWebExchange对象
     * @return true表示是静态资源请求
     */
    public static boolean isStaticResource(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath().toLowerCase();
        
        return path.endsWith(".css") || path.endsWith(".js") || 
               path.endsWith(".png") || path.endsWith(".jpg") || 
               path.endsWith(".jpeg") || path.endsWith(".gif") ||
               path.endsWith(".ico") || path.endsWith(".svg") ||
               path.endsWith(".woff") || path.endsWith(".woff2") ||
               path.endsWith(".ttf") || path.endsWith(".eot") ||
               path.endsWith(".map") || path.endsWith(".webp");
    }
}