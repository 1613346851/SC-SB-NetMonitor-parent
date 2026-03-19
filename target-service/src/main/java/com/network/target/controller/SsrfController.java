package com.network.target.controller;

import com.network.target.common.ApiResponse;
import com.network.target.entity.SsrfLogEntity;
import com.network.target.repository.SsrfLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/target/ssrf")
@Slf4j
public class SsrfController {

    private final RestTemplate restTemplate;
    private final SsrfLogRepository ssrfLogRepository;

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

    public SsrfController(SsrfLogRepository ssrfLogRepository) {
        this.restTemplate = new RestTemplate();
        this.ssrfLogRepository = ssrfLogRepository;
    }

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

            SsrfLogEntity logEntity = new SsrfLogEntity();
            logEntity.setRequestUrl(targetUrl);
            logEntity.setRequestMethod("GET");
            logEntity.setResponseCode(response.getStatusCode().value());
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.length() > 5000) {
                responseBody = responseBody.substring(0, 5000) + "...(已截断)";
            }
            logEntity.setResponseBody(responseBody);
            logEntity.setSourceIp("127.0.0.1");
            ssrfLogRepository.save(logEntity);
            log.info("【数据库存储】已将SSRF请求日志存储到数据库");

            return ApiResponse.success()
                    .message("请求成功（漏洞接口）")
                    .data("target_url", targetUrl)
                    .data("status_code", response.getStatusCode().value())
                    .data("response_body", response.getBody())
                    .data("response_headers", response.getHeaders().toSingleValueMap())
                    .data("db_stored", true)
                    .data("warning", "SSRF漏洞：未对请求URL进行任何限制，可访问内网服务！");

        } catch (Exception e) {
            log.error("SSRF请求异常", e);
            
            SsrfLogEntity logEntity = new SsrfLogEntity();
            logEntity.setRequestUrl(targetUrl);
            logEntity.setRequestMethod("GET");
            logEntity.setResponseCode(0);
            logEntity.setResponseBody("请求失败：" + e.getMessage());
            logEntity.setSourceIp("127.0.0.1");
            ssrfLogRepository.save(logEntity);
            
            return ApiResponse.error()
                    .message("请求失败：" + e.getMessage())
                    .data("target_url", targetUrl)
                    .data("error_type", e.getClass().getSimpleName());
        }
    }

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

    @GetMapping("/logs")
    public ApiResponse getSsrfLogs() {
        List<SsrfLogEntity> logs = ssrfLogRepository.findAll();
        return ApiResponse.success()
                .message("获取SSRF日志成功")
                .data("logs", logs)
                .data("total", logs.size());
    }

    @DeleteMapping("/logs")
    public ApiResponse clearSsrfLogs() {
        int deleted = ssrfLogRepository.deleteAll();
        return ApiResponse.success()
                .message("已清空SSRF日志")
                .data("deleted_count", deleted);
    }

    @GetMapping("/logs/count")
    public ApiResponse getSsrfLogsCount() {
        long count = ssrfLogRepository.count();
        return ApiResponse.success()
                .message("获取SSRF日志数量成功")
                .data("count", count);
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
