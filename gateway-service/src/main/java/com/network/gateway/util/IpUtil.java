package com.network.gateway.util;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;

public class IpUtil {
    // 获取源IP（兼容反向代理）
    public static String getSourceIp(ServerHttpRequest request) {
        // 先从X-Forwarded-For头获取（反向代理场景）
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 多个IP时取第一个
            return ip.split(",")[0].trim();
        }
        // 从请求地址获取
        InetSocketAddress address = request.getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }
}