package com.network.monitor.interceptor;

import com.network.monitor.config.CrossServiceSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 跨服务安全拦截器
 * 验证IP白名单、时间戳、签名
 */
@Slf4j
@Component
public class CrossServiceSecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";
    private static final String HEADER_SIGNATURE = "X-Signature";

    @Autowired
    private CrossServiceSecurityProperties securityProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!securityProperties.isEnabled()) {
            log.debug("跨服务安全验证已禁用");
            return true;
        }

        String clientIp = getClientIp(request);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        String signature = request.getHeader(HEADER_SIGNATURE);

        log.debug("跨服务安全验证: ip={}, requestId={}", clientIp, requestId);

        if (!validateIpWhitelist(clientIp)) {
            log.warn("跨服务请求IP不在白名单: ip={}, requestId={}", clientIp, requestId);
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "IP不在白名单");
            return false;
        }

        if (!validateTimestamp(timestamp)) {
            log.warn("跨服务请求时间戳无效或已过期: ip={}, timestamp={}, requestId={}", clientIp, timestamp, requestId);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "请求时间戳无效或已过期");
            return false;
        }

        if (!validateSignature(timestamp, requestId, signature)) {
            log.warn("跨服务请求签名验证失败: ip={}, requestId={}", clientIp, requestId);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "签名验证失败");
            return false;
        }

        log.debug("跨服务安全验证通过: ip={}, requestId={}", clientIp, requestId);
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean validateIpWhitelist(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }

        List<String> whitelist = securityProperties.getIpWhitelistList();
        if (whitelist.isEmpty()) {
            log.warn("IP白名单为空，拒绝所有请求");
            return false;
        }

        for (String allowedIp : whitelist) {
            allowedIp = allowedIp.trim();
            if (allowedIp.isEmpty()) {
                continue;
            }

            if (matchIp(clientIp, allowedIp)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchIp(String clientIp, String allowedIp) {
        if (allowedIp.equals(clientIp)) {
            return true;
        }

        if (allowedIp.contains("/")) {
            return matchCidr(clientIp, allowedIp);
        }

        return false;
    }

    private boolean matchCidr(String clientIp, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] clientBytes = InetAddress.getByName(clientIp).getAddress();
            byte[] networkBytes = InetAddress.getByName(networkAddress).getAddress();

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (clientBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < clientBytes.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((clientBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("CIDR匹配异常: clientIp={}, cidr={}, error={}", clientIp, cidr, e.getMessage());
            return false;
        }
    }

    private boolean validateTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return false;
        }

        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            long tolerance = securityProperties.getTimestampTolerance();

            return Math.abs(currentTime - requestTime) <= tolerance;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validateSignature(String timestamp, String requestId, String signature) {
        if (timestamp == null || requestId == null || signature == null) {
            return false;
        }

        try {
            String data = timestamp + requestId;
            String expectedSignature = calculateHmacSha256(data, securityProperties.getSecretKey());
            return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("签名验证异常: {}", e.getMessage());
            return false;
        }
    }

    private String calculateHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}", status, message));
    }
}
