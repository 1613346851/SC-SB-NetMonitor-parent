package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SSRF服务端请求伪造漏洞测试接口
 * 核心：模拟URL链接预览功能，服务器代用户发起HTTP请求
 */
@RestController
@RequestMapping("/target/ssrf")
@Slf4j
public class SsrfController {

    private final RestTemplate restTemplate;

    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
            "localhost",
            "127.0.0.1",
            "0.0.0.0"
    );

    private static final List<String> BLOCKED_IP_RANGES = Arrays.asList(
            "10.",
            "172.16.",
            "192.168.",
            "169.254.",
            "0.0.0.0",
            "127."
    );

    private static final int TARGET_PORT = 9001;

    public SsrfController() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 漏洞接口：无限制发起任意URL请求
     * 攻击场景：用户可以请求内网服务、云元数据等
     */
    @GetMapping("/request")
    public ApiResponse makeRequestVulnerable(@RequestParam("url") String targetUrl) {
        try {
            log.warn("【高危SSRF漏洞】尝试请求URL：{}", targetUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            log.warn("【SSRF漏洞触发】成功请求URL：{}, 状态码：{}", targetUrl, response.getStatusCode());

            return ApiResponse.success()
                    .message("请求成功（漏洞接口）")
                    .data("target_url", targetUrl)
                    .data("status_code", response.getStatusCode().value())
                    .data("response_body", response.getBody())
                    .data("response_headers", response.getHeaders().toSingleValueMap())
                    .data("warning", "SSRF漏洞：未对请求URL进行任何限制，可访问内网服务！");

        } catch (Exception e) {
            log.error("SSRF请求异常", e);
            return ApiResponse.error()
                    .message("请求失败：" + e.getMessage())
                    .data("target_url", targetUrl)
                    .data("error_type", e.getClass().getSimpleName());
        }
    }

    /**
     * 安全接口：白名单限制仅本地/靶场内部地址
     * 防护措施：
     * 1. URL格式校验
     * 2. 域名/IP白名单
     * 3. 内网IP段拦截
     * 4. 协议限制（仅HTTP/HTTPS）
     */
    @GetMapping("/safe-request")
    public ApiResponse makeRequestSafe(@RequestParam("url") String targetUrl) {
        try {
            log.info("【安全接口】尝试请求URL：{}", targetUrl);

            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                return ApiResponse.error().message("URL不能为空");
            }

            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                return ApiResponse.error()
                        .message("仅允许HTTP/HTTPS协议")
                        .data("target_url", targetUrl);
            }

            URI uri = new URI(targetUrl);
            String host = uri.getHost();
            int port = uri.getPort();

            if (host == null) {
                return ApiResponse.error()
                        .message("无效的URL格式")
                        .data("target_url", targetUrl);
            }

            String resolvedIp = resolveHost(host);
            if (resolvedIp == null) {
                return ApiResponse.error()
                        .message("无法解析域名")
                        .data("target_url", targetUrl)
                        .data("host", host);
            }

            for (String blockedRange : BLOCKED_IP_RANGES) {
                if (resolvedIp.startsWith(blockedRange)) {
                    boolean isAllowedLocal = ALLOWED_DOMAINS.contains(host) || 
                            (port == TARGET_PORT && resolvedIp.startsWith("127."));
                    
                    if (!isAllowedLocal) {
                        log.warn("【安全拦截】检测到内网IP访问：{} -> {}", host, resolvedIp);
                        return ApiResponse.error()
                                .message("禁止访问内网地址")
                                .data("target_url", targetUrl)
                                .data("host", host)
                                .data("resolved_ip", resolvedIp)
                                .data("blocked_reason", "目标IP属于内网地址段");
                    }
                }
            }

            if (!ALLOWED_DOMAINS.contains(host) && !host.equals("localhost")) {
                log.warn("【安全拦截】域名不在白名单中：{}", host);
                return ApiResponse.error()
                        .message("域名不在允许列表中")
                        .data("target_url", targetUrl)
                        .data("host", host)
                        .data("allowed_domains", ALLOWED_DOMAINS);
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            log.info("【安全接口】成功请求URL：{}", targetUrl);

            return ApiResponse.success()
                    .message("请求成功（安全接口）")
                    .data("target_url", targetUrl)
                    .data("status_code", response.getStatusCode().value())
                    .data("response_body", response.getBody())
                    .data("security_note", "已通过域名白名单和内网IP校验");

        } catch (Exception e) {
            log.error("安全SSRF请求异常", e);
            return ApiResponse.error()
                    .message("请求失败：" + e.getMessage());
        }
    }

    /**
     * 获取允许请求的URL列表
     */
    @GetMapping("/list-allowed")
    public ApiResponse listAllowedUrls() {
        List<Map<String, String>> allowedUrls = Arrays.asList(
                createUrlInfo("http://localhost:9001/target/ddos/status", "DDoS状态接口", "获取靶场DDoS状态"),
                createUrlInfo("http://127.0.0.1:9001/target/path/list-files", "文件列表接口", "获取允许读取的文件列表"),
                createUrlInfo("http://localhost:9001/target/sql/query?id=1", "SQL查询接口", "查询用户数据")
        );

        return ApiResponse.success()
                .message("获取允许URL列表成功")
                .data("allowed_urls", allowedUrls)
                .data("allowed_domains", ALLOWED_DOMAINS)
                .data("target_port", TARGET_PORT);
    }

    private String resolveHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private Map<String, String> createUrlInfo(String url, String name, String desc) {
        Map<String, String> info = new HashMap<>();
        info.put("url", url);
        info.put("name", name);
        info.put("description", desc);
        return info;
    }
}
