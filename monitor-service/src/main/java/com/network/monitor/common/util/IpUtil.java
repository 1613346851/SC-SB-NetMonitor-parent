package com.network.monitor.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 地址处理工具类
 */
public class IpUtil {

    /**
     * 验证 IP 地址格式是否合法
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    /**
     * 获取本机 IP 地址
     */
    public static String getLocalHostIp() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 从请求头中提取真实 IP（考虑 X-Forwarded-For、X-Real-IP 等）
     */
    public static String extractIpFromHeaders(String xForwardedFor, String xRealIp, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int index = xForwardedFor.indexOf(",");
            if (index != -1) {
                return xForwardedFor.substring(0, index).trim();
            }
            return xForwardedFor.trim();
        }

        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }

        return remoteAddr != null ? remoteAddr.trim() : "127.0.0.1";
    }
}
