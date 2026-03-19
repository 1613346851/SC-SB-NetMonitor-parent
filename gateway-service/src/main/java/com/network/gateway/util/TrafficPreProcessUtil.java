package com.network.gateway.util;

import com.network.gateway.bo.RawTrafficBO;
import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.dto.TrafficMonitorDTO;

import java.util.Map;

/**
 * 流量预处理工具类
 * 对原始流量进行标准化处理和异常检测
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class TrafficPreProcessUtil {

    /**
     * 对原始流量进行预处理
     *
     * @param rawTraffic 原始流量对象
     * @return 处理后的流量监控DTO
     */
    public static TrafficMonitorDTO preprocessTraffic(RawTrafficBO rawTraffic) {
        // 首先验证流量有效性
        if (!rawTraffic.isValidTraffic()) {
            throw new IllegalArgumentException("无效的流量数据");
        }

        // 创建流量监控 DTO
        TrafficMonitorDTO monitorDTO = new TrafficMonitorDTO(
                rawTraffic.getRequestId(),
                rawTraffic.getSourceIp(),
                rawTraffic.getMethod(),
                rawTraffic.getUri(),
                rawTraffic.getRequestTime()
        );

        // 设置基本信息
        monitorDTO.setTargetIp(rawTraffic.getTargetIp());
        monitorDTO.setQueryParams(rawTraffic.getQueryParams());
        monitorDTO.setRequestBody(rawTraffic.getRequestBody());
        monitorDTO.setRequestHeaders(rawTraffic.getHeaders());
        monitorDTO.setUserAgent(rawTraffic.getUserAgent());
        monitorDTO.setProtocol(rawTraffic.getProtocol());
        monitorDTO.setSourcePort(rawTraffic.getSourcePort());
        monitorDTO.setTargetPort(rawTraffic.getTargetPort());
        monitorDTO.setContentType(rawTraffic.getContentType());

        // 进行异常检测
        performAbnormalDetection(rawTraffic, monitorDTO);

        // 结构化处理请求参数
        processRequestParameters(rawTraffic, monitorDTO);

        return monitorDTO;
    }

    /**
     * 执行异常流量检测
     *
     * @param rawTraffic 原始流量
     * @param monitorDTO 监控DTO
     */
    private static void performAbnormalDetection(RawTrafficBO rawTraffic, TrafficMonitorDTO monitorDTO) {
        StringBuilder abnormalReasons = new StringBuilder();

        // 检测慢响应
        if (rawTraffic.isSlowResponse(GatewayCacheConstant.ABNORMAL_RESPONSE_TIME_THRESHOLD)) {
            if (abnormalReasons.length() > 0) {
                abnormalReasons.append("; ");
            }
            abnormalReasons.append("响应时间过长(")
                    .append(rawTraffic.getProcessingTime())
                    .append("ms > ")
                    .append(GatewayCacheConstant.ABNORMAL_RESPONSE_TIME_THRESHOLD)
                    .append("ms)");
        }

        // 检测大请求体
        if (rawTraffic.isLargeRequestBody(GatewayCacheConstant.MAX_REQUEST_BODY_SIZE)) {
            if (abnormalReasons.length() > 0) {
                abnormalReasons.append("; ");
            }
            abnormalReasons.append("请求体过大(")
                    .append(rawTraffic.getRequestBody().getBytes().length)
                    .append("字节 > ")
                    .append(GatewayCacheConstant.MAX_REQUEST_BODY_SIZE)
                    .append("字节)");
        }

        // 检测可疑的URI模式
        if (isSuspiciousUri(rawTraffic.getUri())) {
            if (abnormalReasons.length() > 0) {
                abnormalReasons.append("; ");
            }
            abnormalReasons.append("URI包含可疑模式");
        }

        // 检测可疑的查询参数
        if (isSuspiciousQueryParams(rawTraffic.getQueryParams())) {
            if (abnormalReasons.length() > 0) {
                abnormalReasons.append("; ");
            }
            abnormalReasons.append("查询参数包含可疑内容");
        }

        // 检测可疑的用户代理
        if (isSuspiciousUserAgent(rawTraffic.getUserAgent())) {
            if (abnormalReasons.length() > 0) {
                abnormalReasons.append("; ");
            }
            abnormalReasons.append("用户代理可疑");
        }

        // 如果发现异常，标记为异常流量
        if (abnormalReasons.length() > 0) {
            monitorDTO.markAsAbnormal(abnormalReasons.toString());
        }
    }

    /**
     * 处理请求参数的结构化
     *
     * @param rawTraffic 原始流量
     * @param monitorDTO 监控DTO
     */
    private static void processRequestParameters(RawTrafficBO rawTraffic, TrafficMonitorDTO monitorDTO) {
        // 处理查询参数 - 清理敏感信息
        Map<String, String> cleanQueryParams = cleanSensitiveParams(rawTraffic.getQueryParams());
        monitorDTO.setQueryParams(cleanQueryParams);

        // 处理请求体 - 如果是JSON格式，可以进一步解析
        String requestBody = rawTraffic.getRequestBody();
        if (requestBody != null && isJsonContent(rawTraffic.getHeaders())) {
            // 可以在这里添加JSON解析和敏感字段清理逻辑
            // 简化处理：截取前1000字符避免过长
            if (requestBody.length() > 1000) {
                requestBody = requestBody.substring(0, 1000) + "...(truncated)";
            }
        }
        monitorDTO.setRequestBody(requestBody);

        // 处理请求头 - 清理敏感头部
        Map<String, String> cleanHeaders = cleanSensitiveHeaders(rawTraffic.getHeaders());
        monitorDTO.setRequestHeaders(cleanHeaders);
    }

    /**
     * 检测URI是否包含可疑模式
     *
     * @param uri URI字符串
     * @return true表示可疑
     */
    private static boolean isSuspiciousUri(String uri) {
        if (uri == null) {
            return false;
        }

        // 常见的攻击模式关键词
        String[] suspiciousPatterns = {
                "../", "..\\", "%2e%2e/", "%252e%252e/",
                "union select", "select ", "insert ", "delete ",
                "drop table", "create table", "exec(", "execute(",
                "<script", "javascript:", "onload=", "onclick=",
                "eval(", "document.cookie", "document.location"
        };

        String lowerUri = uri.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerUri.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测查询参数是否包含可疑内容
     *
     * @param queryParams 查询参数Map
     * @return true表示可疑
     */
    private static boolean isSuspiciousQueryParams(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String paramName = entry.getKey().toLowerCase();
            String paramValue = entry.getValue();

            // 检查参数名是否可疑
            if (isSuspiciousParameterName(paramName)) {
                return true;
            }

            // 检查参数值是否可疑
            if (paramValue != null && isSuspiciousParameterValue(paramValue)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查参数名是否可疑
     *
     * @param paramName 参数名
     * @return true表示可疑
     */
    private static boolean isSuspiciousParameterName(String paramName) {
        String[] suspiciousNames = {
                "sql", "script", "eval", "exec", "cmd", "command",
                "shell", "php", "jsp", "asp", "jspx", "aspx"
        };

        for (String name : suspiciousNames) {
            if (paramName.contains(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查参数值是否可疑
     *
     * @param paramValue 参数值
     * @return true表示可疑
     */
    private static boolean isSuspiciousParameterValue(String paramValue) {
        if (paramValue == null) {
            return false;
        }

        String lowerValue = paramValue.toLowerCase();
        
        // SQL注入模式
        if (lowerValue.contains("union select") || 
            lowerValue.contains("' or '1'='1") ||
            lowerValue.contains("'; drop table") ||
            lowerValue.matches(".*'[\\s]*or[\\s]*'.*")) {
            return true;
        }

        // XSS攻击模式
        if (lowerValue.contains("<script") ||
            lowerValue.contains("javascript:") ||
            lowerValue.contains("onerror=") ||
            lowerValue.contains("onload=")) {
            return true;
        }

        return false;
    }

    /**
     * 检测用户代理是否可疑
     *
     * @param userAgent 用户代理字符串
     * @return true表示可疑
     */
    private static boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return true; // 空的User-Agent很可疑
        }

        String lowerAgent = userAgent.toLowerCase();

        // 自动化工具特征
        String[] botPatterns = {
                "sqlmap", "nessus", "nmap", "burp", "zaproxy",
                "curl/", "wget/", "python-", "java/", "apache-httpclient"
        };

        for (String pattern : botPatterns) {
            if (lowerAgent.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 清理敏感参数
     *
     * @param params 参数Map
     * @return 清理后的参数Map
     */
    private static Map<String, String> cleanSensitiveParams(Map<String, String> params) {
        if (params == null) {
            return null;
        }

        // 敏感参数名（需要脱敏处理）
        String[] sensitiveParams = {
                "password", "pwd", "pass", "token", "auth",
                "creditcard", "ssn", "phone", "email"
        };

        for (String sensitiveParam : sensitiveParams) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().toLowerCase().contains(sensitiveParam)) {
                    entry.setValue("***MASKED***");
                }
            }
        }

        return params;
    }

    /**
     * 清理敏感请求头
     *
     * @param headers 请求头Map
     * @return 清理后的请求头Map
     */
    private static Map<String, String> cleanSensitiveHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }

        // 敏感头部名
        String[] sensitiveHeaders = {
                "authorization", "cookie", "x-api-key", "x-auth-token"
        };

        for (String sensitiveHeader : sensitiveHeaders) {
            if (headers.containsKey(sensitiveHeader)) {
                headers.put(sensitiveHeader, "***MASKED***");
            }
        }

        return headers;
    }

    /**
     * 判断是否为JSON内容
     *
     * @param headers 请求头
     * @return true表示是JSON内容
     */
    private static boolean isJsonContent(Map<String, String> headers) {
        String contentType = headers.get("content-type");
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    /**
     * 格式化流量摘要信息
     *
     * @param rawTraffic 原始流量
     * @return 格式化的摘要
     */
    public static String formatTrafficSummary(RawTrafficBO rawTraffic) {
        StringBuilder sb = new StringBuilder();
        sb.append("流量摘要: ");
        sb.append("ID[").append(rawTraffic.getRequestId()).append("] ");
        sb.append("IP[").append(rawTraffic.getSourceIp()).append("] ");
        sb.append("方法[").append(rawTraffic.getMethod()).append("] ");
        sb.append("URI[").append(rawTraffic.getUri()).append("] ");
        
        if (rawTraffic.getProcessingTime() != null) {
            sb.append("耗时[").append(rawTraffic.getProcessingTime()).append("ms] ");
        }
        
        if (rawTraffic.getStatusCode() != null) {
            sb.append("状态[").append(rawTraffic.getStatusCode()).append("] ");
        }
        
        if (rawTraffic.getAbnormalTraffic()) {
            sb.append("异常原因[").append(rawTraffic.getAbnormalReason()).append("]");
        }

        return sb.toString();
    }
}