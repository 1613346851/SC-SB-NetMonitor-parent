package com.network.gateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 跨服务安全工具类
 * 用于生成请求签名和安全头
 */
public class CrossServiceSecurityUtil {

    private static final Logger logger = LoggerFactory.getLogger(CrossServiceSecurityUtil.class);

    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";
    private static final String HEADER_SIGNATURE = "X-Signature";

    /**
     * 生成跨服务请求的安全头
     *
     * @param requestId 请求ID
     * @param secretKey 密钥
     * @return 包含时间戳、请求ID、签名的请求头Map
     */
    public static Map<String, String> generateSecurityHeaders(String requestId, String secretKey) {
        Map<String, String> headers = new HashMap<>();
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = generateSignature(timestamp, requestId, secretKey);
        
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_REQUEST_ID, requestId);
        headers.put(HEADER_SIGNATURE, signature);
        
        logger.debug("生成安全头: requestId={}, timestamp={}", requestId, timestamp);
        
        return headers;
    }

    /**
     * 生成HMAC-SHA256签名
     *
     * @param timestamp 时间戳
     * @param requestId 请求ID
     * @param secretKey 密钥
     * @return 签名字符串（十六进制）
     */
    public static String generateSignature(String timestamp, String requestId, String secretKey) {
        try {
            String data = timestamp + requestId;
            return calculateHmacSha256(data, secretKey);
        } catch (Exception e) {
            logger.error("生成签名失败: requestId={}, error={}", requestId, e.getMessage());
            throw new RuntimeException("生成签名失败", e);
        }
    }

    /**
     * 计算HMAC-SHA256
     *
     * @param data 待签名数据
     * @param key  密钥
     * @return 十六进制签名字符串
     */
    private static String calculateHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
