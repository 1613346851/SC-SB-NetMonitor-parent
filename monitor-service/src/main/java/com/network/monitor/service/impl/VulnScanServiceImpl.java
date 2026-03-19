package com.network.monitor.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.common.constant.VerifyStatusConstant;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.service.AiModelService;
import com.network.monitor.service.VulnScanService;
import com.network.monitor.service.VulnerabilityVerifyService;
import com.network.monitor.util.payload.CommandInjectionPayload;
import com.network.monitor.util.payload.PathTraversalPayload;
import com.network.monitor.util.payload.PayloadCase;
import com.network.monitor.util.payload.SqlInjectionPayload;
import com.network.monitor.util.payload.XssPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 主动漏洞扫描服务实现
 */
@Slf4j
@Service
public class VulnScanServiceImpl implements VulnScanService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STATUS_IDLE = "IDLE";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_TERMINATED = "TERMINATED";
    private static final String STATUS_FAILED = "FAILED";

    @Value("${vuln.scan.gateway-base-url:http://localhost:9000}")
    private String gatewayBaseUrl;

    @Value("${vuln.scan.target-display-url:http://127.0.0.1:9001}")
    private String targetDisplayUrl;

    @Value("${vuln.scan.history-size:10}")
    private int historySize;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private VulnerabilityVerifyService vulnerabilityVerifyService;

    @Autowired
    private AiModelService aiModelService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "vuln-scan-executor");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();
    private final Deque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();

    private volatile ScanState currentState = ScanState.idle();

    @Override
    public Map<String, Object> startScan(String scanType) {
        String normalizedType = normalizeScanType(scanType);
        synchronized (stateLock) {
            if (currentState.isActive()) {
                return buildStateResponse(currentState, "当前已有扫描任务正在执行，请先暂停或终止后再试");
            }

            List<ScanPlan> plans = detectInterfaces(normalizedType);
            ScanState state = ScanState.start(normalizedType, targetDisplayUrl, plans);
            currentState = state;

            executorService.submit(() -> executeScan(state));
            log.info("主动扫描任务已启动：taskId={}, scanType={}, totalInterfaces={}",
                    state.taskId, normalizedType, state.totalInterfaces);
            return buildStateResponse(state, "扫描任务已启动");
        }
    }

    @Override
    public Map<String, Object> getScanProgress() {
        ScanState state = currentState;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", state.taskId);
        result.put("status", state.status);
        result.put("scanType", state.scanType);
        result.put("target", state.target);
        result.put("currentStep", state.currentStep);
        result.put("progressPercent", state.getProgressPercent());
        result.put("completedInterfaces", state.completedInterfaces);
        result.put("totalInterfaces", state.totalInterfaces);
        result.put("discoveredCount", state.discoveredCount);
        result.put("startTime", formatTime(state.startTime));
        result.put("endTime", formatTime(state.endTime));
        result.put("summary", state.summary);
        result.put("lastMessage", state.lastMessage);
        result.put("interfaces", state.planSummaries);
        result.put("warnings", new ArrayList<>(state.warnings));
        return result;

    }

    @Override
    public Map<String, Object> getScanResult() {
        ScanState state = currentState;
        Map<String, Object> result = buildStateResponse(state, state.lastMessage);
        result.put("results", state.getSortedResults());
        result.put("history", new ArrayList<>(history));
        return result;
    }

    @Override
    public Map<String, Object> syncCurrentResults() {
        ScanState state = currentState;
        int syncedCount = 0;
        List<Map<String, Object>> syncedResults = state.getSortedResults();
        for (Map<String, Object> item : syncedResults) {
            Object synced = item.get("synced");
            if (Boolean.TRUE.equals(synced)) {
                continue;
            }
            try {
                VulnerabilityMonitorEntity entity = buildVulnerabilityEntity(item);
                VulnerabilityMonitorEntity saved = vulnerabilityVerifyService.saveOrUpdateVulnerability(entity);
                item.put("synced", true);
                item.put("syncTime", formatTime(LocalDateTime.now()));
                item.put("vulnerabilityId", saved != null ? saved.getId() : null);
                syncedCount++;
            } catch (Exception e) {
                log.warn("手动同步扫描结果失败：path={}, msg={}", item.get("vulnPath"), e.getMessage());
                item.put("syncError", e.getMessage());
            }
        }
        state.lastMessage = syncedCount > 0 ? "已手动同步 " + syncedCount + " 条漏洞结果" : "当前结果已全部同步";
        Map<String, Object> result = buildStateResponse(state, state.lastMessage);
        result.put("syncedCount", syncedCount);
        result.put("results", syncedResults);
        return result;
    }

    @Override
    public Map<String, Object> controlScan(String action) {
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        ScanState state = currentState;
        if (!state.hasTask()) {
            return buildStateResponse(state, "当前没有可控制的扫描任务");
        }

        switch (normalizedAction) {
            case "PAUSE" -> {
                if (STATUS_RUNNING.equals(state.status)) {
                    state.status = STATUS_PAUSED;
                    state.lastMessage = "扫描任务已暂停";
                }
            }
            case "RESUME" -> {
                if (STATUS_PAUSED.equals(state.status)) {
                    state.status = STATUS_RUNNING;
                    state.lastMessage = "扫描任务已恢复";
                }
            }
            case "TERMINATE" -> {
                if (state.isActive()) {
                    state.status = STATUS_TERMINATED;
                    state.endTime = LocalDateTime.now();
                    state.lastMessage = "扫描任务已终止";
                }
            }
            default -> state.lastMessage = "不支持的操作，仅支持 PAUSE / RESUME / TERMINATE";
        }
        return buildStateResponse(state, state.lastMessage);
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    private void executeScan(ScanState state) {
        try {
            state.currentStep = "校验靶场在线状态";
            if (!checkTargetOnline()) {
                state.status = STATUS_FAILED;
                state.endTime = LocalDateTime.now();
                state.lastMessage = "靶场服务不可达，请确认 gateway-service(9000) 与 target-service(9001) 已启动";
                state.summary = state.lastMessage;
                appendHistory(state);
                return;
            }

            for (ScanPlan plan : state.plans) {
                if (!waitIfPausedOrTerminated(state)) {
                    finalizeTerminated(state);
                    return;
                }

                state.currentStep = "扫描接口 " + plan.path + "（" + plan.title + "）";
                try {
                    ScanFinding finding = plan.operation.execute();
                    if (finding != null) {
                        Map<String, Object> resultItem = finding.toMap();
                        autoInsertVuln(state, resultItem);
                        state.results.add(resultItem);
                        state.discoveredCount = state.results.size();
                    }
                } catch (Exception ex) {
                    log.warn("扫描接口失败：path={}, msg={}", plan.path, ex.getMessage());
                    state.warnings.add(plan.path + " 扫描失败：" + ex.getMessage());
                } finally {
                    state.completedInterfaces++;
                }
            }

            if (STATUS_TERMINATED.equals(state.status)) {
                finalizeTerminated(state);
                return;
            }

            state.currentStep = "汇总扫描结果";
            // 【AI全局调用】智能生成扫描总结
            String aiSummary = aiModelService.summaryReport(buildSummaryContext(state));
            state.summary = aiSummary != null && !aiSummary.isBlank() ? aiSummary : buildSummary(state);
            state.status = STATUS_COMPLETED;
            state.endTime = LocalDateTime.now();
            state.lastMessage = "扫描完成，共发现 " + state.discoveredCount + " 项漏洞";
            appendHistory(state);
            log.info("主动扫描任务完成：taskId={}, discovered={}", state.taskId, state.discoveredCount);
        } catch (Exception e) {
            log.error("主动扫描任务执行异常：taskId={}", state.taskId, e);
            state.status = STATUS_FAILED;
            state.endTime = LocalDateTime.now();
            state.lastMessage = "扫描执行失败：" + e.getMessage();
            state.summary = state.lastMessage;
            appendHistory(state);
        }
    }

    private List<ScanPlan> detectInterfaces(String scanType) {
        List<ScanPlan> plans = new ArrayList<>();
        plans.add(new ScanPlan("SQL 注入 - 用户查询", "SQL_INJECTION", "HIGH", "GET", "/target/sql/query",
                () -> probeSqlInjection(scanType)));
        plans.add(new ScanPlan("XSS - 反射型搜索", "XSS", "MEDIUM", "GET", "/target/xss/search",
                () -> probeReflectiveXss(scanType)));
        plans.add(new ScanPlan("XSS - DOM 用户资料", "XSS", "MEDIUM", "GET", "/target/xss/profile",
                () -> probeDomXss(scanType)));
        plans.add(new ScanPlan("命令注入 - 系统命令执行", "COMMAND_INJECTION", "CRITICAL", "GET", "/target/cmd/execute",
                () -> probeCommandInjection(scanType)));
        plans.add(new ScanPlan("路径遍历 - 文件读取", "PATH_TRAVERSAL", "HIGH", "GET", "/target/path/read",
                () -> probePathTraversal(scanType)));

        if (isFullScan(scanType)) {
            plans.add(new ScanPlan("文件包含 - 动态资源加载", "FILE_INCLUSION", "HIGH", "GET", "/target/file/include",
                    this::probeFileInclude));
            plans.add(new ScanPlan("SSRF - 服务端请求转发", "SSRF", "HIGH", "GET", "/target/ssrf/request",
                    this::probeSsrf));
            plans.add(new ScanPlan("XXE - 外部实体解析", "XXE", "HIGH", "POST", "/target/xxe/parse",
                    this::probeXxe));
            plans.add(new ScanPlan("CSRF - 未授权状态修改", "CSRF", "MEDIUM", "POST", "/target/csrf/update-name",
                    this::probeCsrf));
        }

        return plans;
    }

    private boolean checkTargetOnline() {
        try {
            ProbeResponse response = doGet("/target/ddos/status", Collections.emptyMap());
            return response.isOk() && containsIgnoreCase(response.body, "DDoS被攻击目标状态");
        } catch (Exception e) {
            log.warn("靶场在线状态检查失败：{}", e.getMessage());
            return false;
        }
    }

    private boolean waitIfPausedOrTerminated(ScanState state) {
        while (STATUS_PAUSED.equals(state.status)) {
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                state.status = STATUS_TERMINATED;
                state.lastMessage = "扫描线程已中断";
                return false;
            }
        }
        return !STATUS_TERMINATED.equals(state.status);
    }

    private void finalizeTerminated(ScanState state) {
        state.endTime = state.endTime == null ? LocalDateTime.now() : state.endTime;
        state.summary = buildSummary(state);
        state.lastMessage = state.lastMessage == null ? "扫描任务已终止" : state.lastMessage;
        appendHistory(state);
    }

    private ScanFinding probeSqlInjection(String scanType) {
        List<PayloadCase> payloads = SqlInjectionPayload.getPayloads(scanType);
        // 【AI全局调用】生成智能Payload
        List<String> aiPayloads = aiModelService.generatePayload("SQL_INJECTION", "/target/sql/query",
                payloads.stream().map(PayloadCase::payload).collect(Collectors.joining(",")));
        List<PayloadCase> mergedPayloads = mergePayloads(payloads, aiPayloads);

        for (PayloadCase payload : mergedPayloads) {
            ProbeResponse response = doGet("/target/sql/query", Map.of("id", payload.payload()));
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "SQL注入漏洞", "多语句执行成功", "executed_sql")) {
                return buildFinding("SQL 注入漏洞 - 用户查询接口", "SQL_INJECTION", "HIGH",
                        "/target/sql/query", "GET", payload.payload(), payload.matchKeyword(),
                        "发现未经参数化处理的 SQL 查询，可被构造恒真条件或堆叠语句绕过。", response.body,
                        defaultFixSuggestion("SQL_INJECTION", "/target/sql/query"));
            }
        }
        return null;
    }

    private ScanFinding probeReflectiveXss(String scanType) {
        PayloadCase payload = XssPayload.getPayloads(scanType).get(0);
        ProbeResponse response = doGet("/target/xss/search", Map.of("keyword", payload.payload()));
        if (containsIgnoreCase(response.body, payload.matchKeyword())
                && containsAny(response.body, "反射型XSS漏洞", "搜索成功")) {
            return buildFinding("XSS 漏洞 - 反射型搜索接口", "XSS", "MEDIUM",
                    "/target/xss/search", "GET", payload.payload(), payload.matchKeyword(),
                    "搜索关键词被直接拼接到响应中，前端渲染时可能执行恶意脚本。", response.body,
                    defaultFixSuggestion("XSS", "/target/xss/search"));
        }
        return null;
    }

    private ScanFinding probeDomXss(String scanType) {
        PayloadCase payload = XssPayload.getPayloads(scanType).get(0);
        ProbeResponse response = doGet("/target/xss/profile", Map.of("username", payload.payload()));
        if (containsIgnoreCase(response.body, payload.matchKeyword())
                && containsAny(response.body, "DOM型XSS漏洞", "获取资料成功")) {
            return buildFinding("XSS 漏洞 - DOM 资料渲染接口", "XSS", "MEDIUM",
                    "/target/xss/profile", "GET", payload.payload(), payload.matchKeyword(),
                    "后端返回未转义的 HTML 片段，前端若直接使用 innerHTML 渲染将触发脚本执行。", response.body,
                    defaultFixSuggestion("XSS", "/target/xss/profile"));
        }
        return null;
    }

    private ScanFinding probeCommandInjection(String scanType) {
        for (PayloadCase payload : CommandInjectionPayload.getPayloads(scanType)) {
            ProbeResponse response = doGet("/target/cmd/execute", Map.of("cmd", payload.payload()));
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "命令执行结果", "纯命令注入漏洞触发成功")) {
                return buildFinding("命令注入漏洞 - 系统命令执行接口", "COMMAND_INJECTION", "CRITICAL",
                        "/target/cmd/execute", "GET", payload.payload(), payload.matchKeyword(),
                        "用户输入被直接拼接为系统命令执行，具备远程命令执行风险。", response.body,
                        defaultFixSuggestion("COMMAND_INJECTION", "/target/cmd/execute"));
            }
        }
        return null;
    }

    private ScanFinding probePathTraversal(String scanType) {
        for (PayloadCase payload : PathTraversalPayload.getPayloads(scanType)) {
            ProbeResponse response = doGet("/target/path/read", Map.of("filename", payload.payload()));
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "路径遍历漏洞", "文件读取成功")) {
                return buildFinding("路径遍历漏洞 - 文件读取接口", "PATH_TRAVERSAL", "HIGH",
                        "/target/path/read", "GET", payload.payload(), payload.matchKeyword(),
                        "文件名未经严格约束即参与路径拼接，可读取项目资源目录之外的敏感文件。", response.body,
                        defaultFixSuggestion("PATH_TRAVERSAL", "/target/path/read"));
            }
        }
        return null;
    }

    private ScanFinding probeFileInclude() {
        String payload = "config/test.properties";
        ProbeResponse response = doGet("/target/file/include", Map.of("path", payload));
        if (containsAny(response.body, "文件包含漏洞", "db.password", "文件加载成功")) {
            return buildFinding("文件包含漏洞 - 动态资源加载接口", "FILE_INCLUSION", "HIGH",
                    "/target/file/include", "GET", payload, "db.password",
                    "用户可控制待加载资源路径，导致本地配置文件和敏感内容被直接回显。", response.body,
                    defaultFixSuggestion("FILE_INCLUSION", "/target/file/include"));
        }
        return null;
    }

    private ScanFinding probeSsrf() {
        String payload = gatewayBaseUrl + "/target/ddos/status";
        ProbeResponse response = doGet("/target/ssrf/request", Map.of("url", payload));
        if (containsAny(response.body, "SSRF漏洞", "DDoS被攻击目标状态", "请求成功（漏洞接口）")) {
            return buildFinding("SSRF 漏洞 - 服务端请求转发接口", "SSRF", "HIGH",
                    "/target/ssrf/request", "GET", payload, "DDoS被攻击目标状态",
                    "服务端对外部 URL 无约束访问，可被用来探测内网或读取受信任服务响应。", response.body,
                    defaultFixSuggestion("SSRF", "/target/ssrf/request"));
        }
        return null;
    }

    private ScanFinding probeXxe() {
        ProbeResponse cases = doGet("/target/xxe/test-cases", Collections.emptyMap());
        Map<String, Object> caseBody = parseBody(cases.body);
        Map<String, Object> caseData = getNestedMap(caseBody, "data");
        Map<String, Object> testCases = getNestedMap(caseData, "test_cases");
        String xmlPayload = Objects.toString(testCases.get("xxe_project_config"), "");
        if (xmlPayload.isBlank()) {
            return null;
        }
        ProbeResponse response = doPostText("/target/xxe/parse", xmlPayload, MediaType.APPLICATION_XML);
        if (containsAny(response.body, "XXE漏洞", "has_external_entity", "test_password_123")) {
            return buildFinding("XXE 漏洞 - XML 外部实体解析接口", "XXE", "HIGH",
                    "/target/xxe/parse", "POST", "<!DOCTYPE ...>", "has_external_entity",
                    "XML 解析器未禁用外部实体，攻击者可借此读取项目内文件内容。", response.body,
                    defaultFixSuggestion("XXE", "/target/xxe/parse"));
        }
        return null;
    }

    private ScanFinding probeCsrf() {
        String nickname = "scan_csrf_user";
        ProbeResponse response = doPostForm("/target/csrf/update-name", Map.of(
                "userId", "1",
                "nickname", nickname
        ));
        if (containsAny(response.body, "CSRF漏洞", nickname, "昵称修改成功（漏洞接口）")) {
            return buildFinding("CSRF 漏洞 - 用户昵称修改接口", "CSRF", "MEDIUM",
                    "/target/csrf/update-name", "POST", nickname, nickname,
                    "状态修改接口缺少 CSRF Token 校验，可被第三方站点诱导发起跨站请求。", response.body,
                    defaultFixSuggestion("CSRF", "/target/csrf/update-name"));
        }
        return null;
    }

    private List<PayloadCase> mergePayloads(List<PayloadCase> defaults, List<String> aiPayloads) {
        if (aiPayloads == null || aiPayloads.isEmpty()) {
            return defaults;
        }
        List<PayloadCase> merged = new ArrayList<>(defaults);
        for (String aiPayload : aiPayloads) {
            if (aiPayload != null && !aiPayload.isBlank()) {
                merged.add(new PayloadCase(aiPayload, aiPayload, "AI预留生成Payload"));
            }
        }
        return merged;
    }

    private void autoInsertVuln(ScanState state, Map<String, Object> item) {
        try {
            VulnerabilityMonitorEntity entity = buildVulnerabilityEntity(item);
            // 【AI全局调用】自动生成修复建议
            String aiFixSuggestion = aiModelService.generateFixSuggestion(entity.getVulnType(), entity.getVulnPath());
            if (aiFixSuggestion != null && !aiFixSuggestion.isBlank()) {
                entity.setFixSuggestion(aiFixSuggestion);
            }
            VulnerabilityMonitorEntity saved = vulnerabilityVerifyService.saveOrUpdateVulnerability(entity);
            item.put("synced", true);
            item.put("syncTime", formatTime(LocalDateTime.now()));
            item.put("vulnerabilityId", saved != null ? saved.getId() : null);
        } catch (Exception e) {
            log.warn("扫描结果自动入库失败：path={}, msg={}", item.get("vulnPath"), e.getMessage());
            item.put("synced", false);
            item.put("syncError", e.getMessage());
            state.warnings.add("自动入库失败：" + item.get("vulnPath") + " - " + e.getMessage());
        }
    }

    private VulnerabilityMonitorEntity buildVulnerabilityEntity(Map<String, Object> item) {
        VulnerabilityMonitorEntity entity = new VulnerabilityMonitorEntity();
        entity.setVulnName(Objects.toString(item.get("vulnName"), ""));
        entity.setVulnType(Objects.toString(item.get("vulnType"), ""));
        entity.setVulnLevel(Objects.toString(item.get("vulnLevel"), "MEDIUM"));
        entity.setVulnPath(Objects.toString(item.get("vulnPath"), ""));
        entity.setVerifyStatus(VerifyStatusConstant.VERIFIED_EXPLOITABLE);
        entity.setAttackCount(0);
        entity.setDescription(Objects.toString(item.get("description"), "") + "\n来源：主动扫描\n扫描时间：" + item.get("detectedAt"));
        entity.setFixSuggestion(Objects.toString(item.get("fixSuggestion"), ""));
        return entity;
    }

    private ScanFinding buildFinding(String vulnName,
                                     String vulnType,
                                     String vulnLevel,
                                     String vulnPath,
                                     String method,
                                     String payload,
                                     String matchedKeyword,
                                     String description,
                                     String responseBody,
                                     String fixSuggestion) {
        // 【AI全局调用】AI智能验证漏洞
        String aiVerdict = aiModelService.analyzeVuln(vulnType, responseBody, vulnPath);
        // 【AI全局调用】风险等级智能评估
        String aiRiskLevel = aiModelService.analyzeAttack(payload, responseBody);
        String finalLevel = normalizeRiskLevel(aiRiskLevel, vulnLevel);
        return new ScanFinding(
                vulnName,
                vulnType,
                finalLevel,
                vulnPath,
                method,
                payload,
                matchedKeyword,
                description,
                summarizeResponse(responseBody),
                fixSuggestion,
                aiVerdict
        );
    }

    private ProbeResponse doGet(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(gatewayBaseUrl).path(path);
        params.forEach(builder::queryParam);
        ResponseEntity<String> response = restTemplate.getForEntity(builder.build().encode().toUri(), String.class);

        return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
    }

    private ProbeResponse doPostForm(String path, Map<String, String> formData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = formData.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encodeValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(gatewayBaseUrl + path, entity, String.class);
        return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
    }

    private ProbeResponse doPostText(String path, String text, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        HttpEntity<String> entity = new HttpEntity<>(text, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(gatewayBaseUrl + path, entity, String.class);
        return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
    }

    private String encodeValue(String value) {
        return UriComponentsBuilder.newInstance().queryParam("v", value).build().encode().toUriString().substring(3);
    }

    private Map<String, Object> parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.entrySet().stream().collect(Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    LinkedHashMap::new
            ));
        }
        return Collections.emptyMap();
    }

    private String buildSummary(ScanState state) {
        return String.format("扫描状态：%s；已完成 %d/%d 个接口检测；发现 %d 项漏洞。",
                state.status, state.completedInterfaces, state.totalInterfaces, state.discoveredCount);
    }

    private String buildSummaryContext(ScanState state) {
        return "scanType=" + state.scanType + ", target=" + state.target + ", discovered=" + state.discoveredCount
                + ", interfaces=" + state.completedInterfaces + "/" + state.totalInterfaces;
    }

    private void appendHistory(ScanState state) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", state.taskId);
        summary.put("status", state.status);
        summary.put("scanType", state.scanType);
        summary.put("target", state.target);
        summary.put("discoveredCount", state.discoveredCount);
        summary.put("completedInterfaces", state.completedInterfaces);
        summary.put("totalInterfaces", state.totalInterfaces);
        summary.put("startTime", formatTime(state.startTime));
        summary.put("endTime", formatTime(state.endTime));
        summary.put("durationSeconds", state.getDurationSeconds());
        summary.put("summary", state.summary);
        history.addFirst(summary);
        while (history.size() > historySize) {
            history.removeLast();
        }
    }

    private Map<String, Object> buildStateResponse(ScanState state, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", state.taskId);
        result.put("status", state.status);
        result.put("scanType", state.scanType);
        result.put("target", state.target);
        result.put("message", message);
        result.put("currentStep", state.currentStep);
        result.put("progressPercent", state.getProgressPercent());
        result.put("completedInterfaces", state.completedInterfaces);
        result.put("totalInterfaces", state.totalInterfaces);
        result.put("discoveredCount", state.discoveredCount);
        result.put("startTime", formatTime(state.startTime));
        result.put("endTime", formatTime(state.endTime));
        result.put("durationSeconds", state.getDurationSeconds());
        result.put("summary", state.summary);
        result.put("interfaces", state.planSummaries);
        result.put("warnings", new ArrayList<>(state.warnings));
        return result;
    }

    private String summarizeResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        String compact = responseBody.replaceAll("\\s+", " ").trim();
        return compact.length() > 320 ? compact.substring(0, 320) + "..." : compact;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }

    private String normalizeScanType(String scanType) {
        return "FULL".equalsIgnoreCase(scanType) ? "FULL" : "QUICK";
    }

    private boolean isFullScan(String scanType) {
        return "FULL".equalsIgnoreCase(scanType);
    }

    private String normalizeRiskLevel(String aiRiskLevel, String defaultLevel) {
        if (aiRiskLevel == null || aiRiskLevel.isBlank()) {
            return defaultLevel;
        }
        String normalized = aiRiskLevel.trim().toUpperCase(Locale.ROOT);
        return Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(normalized) ? normalized : defaultLevel;
    }

    private boolean containsAny(String content, String... keywords) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return Arrays.stream(keywords).anyMatch(keyword -> containsIgnoreCase(content, keyword));
    }

    private boolean containsIgnoreCase(String content, String keyword) {
        if (content == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        return content.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String defaultFixSuggestion(String vulnType, String vulnPath) {
        return switch (vulnType) {
            case "SQL_INJECTION" -> "对接口 " + vulnPath + " 使用参数化查询或预编译语句，禁止拼接 SQL。";
            case "XSS" -> "对接口 " + vulnPath + " 的输入输出进行 HTML 转义，并避免使用 innerHTML 渲染未信任内容。";
            case "COMMAND_INJECTION" -> "对接口 " + vulnPath + " 建立命令白名单，禁止将用户输入直接传入系统命令。";
            case "PATH_TRAVERSAL" -> "对接口 " + vulnPath + " 执行白名单文件校验与规范化路径检查。";
            case "FILE_INCLUSION" -> "限制接口 " + vulnPath + " 仅加载预定义资源，并校验文件类型与资源根目录。";
            case "SSRF" -> "为接口 " + vulnPath + " 增加协议、域名和内网 IP 白名单校验。";
            case "XXE" -> "对接口 " + vulnPath + " 禁用 DTD 与外部实体解析。";
            case "CSRF" -> "为接口 " + vulnPath + " 启用 CSRF Token 校验，并校验请求来源。";
            default -> "请为接口 " + vulnPath + " 增加输入校验、最小权限和安全编码防护。";
        };
    }

    private static final class ProbeResponse {
        private final int statusCode;
        private final String body;

        private ProbeResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        private boolean isOk() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    @FunctionalInterface
    private interface ScanOperation {
        ScanFinding execute();
    }

    private record ScanPlan(String title, String vulnType, String riskLevel, String method, String path,
                            ScanOperation operation) {
        Map<String, Object> toSummary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("title", title);
            summary.put("vulnType", vulnType);
            summary.put("riskLevel", riskLevel);
            summary.put("method", method);
            summary.put("path", path);
            return summary;
        }
    }

    private record ScanFinding(String vulnName,
                               String vulnType,
                               String vulnLevel,
                               String vulnPath,
                               String requestMethod,
                               String payload,
                               String matchedKeyword,
                               String description,
                               String responseSnippet,
                               String fixSuggestion,
                               String aiVerdict) {
        Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("vulnName", vulnName);
            item.put("vulnType", vulnType);
            item.put("vulnLevel", vulnLevel);
            item.put("vulnPath", vulnPath);
            item.put("requestMethod", requestMethod);
            item.put("payload", payload);
            item.put("matchedKeyword", matchedKeyword);
            item.put("description", description);
            item.put("responseSnippet", responseSnippet);
            item.put("fixSuggestion", fixSuggestion);
            item.put("aiVerdict", aiVerdict);
            item.put("detectedAt", LocalDateTime.now().format(TIME_FORMATTER));
            item.put("source", "AUTO_SCAN");
            item.put("verifyStatus", VerifyStatusConstant.VERIFIED_EXPLOITABLE);
            item.put("synced", false);
            return item;
        }
    }

    private static final class ScanState {
        private final String taskId;
        private final String scanType;
        private final String target;
        private final List<ScanPlan> plans;
        private final List<Map<String, Object>> planSummaries;
        private final List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        private final LocalDateTime startTime;

        private volatile String status;
        private volatile String currentStep;
        private volatile int completedInterfaces;
        private volatile int discoveredCount;
        private volatile LocalDateTime endTime;
        private volatile String summary;
        private volatile String lastMessage;
        private final int totalInterfaces;

        private ScanState(String taskId,
                          String scanType,
                          String target,
                          String status,
                          String currentStep,
                          List<ScanPlan> plans,
                          List<Map<String, Object>> planSummaries,
                          LocalDateTime startTime) {
            this.taskId = taskId;
            this.scanType = scanType;
            this.target = target;
            this.status = status;
            this.currentStep = currentStep;
            this.plans = plans;
            this.planSummaries = planSummaries;
            this.startTime = startTime;
            this.totalInterfaces = plans.size();
            this.lastMessage = "等待启动";
        }

        static ScanState idle() {
            return new ScanState(null, null, null, STATUS_IDLE, "暂无扫描任务",
                    Collections.emptyList(), Collections.emptyList(), null);
        }

        static ScanState start(String scanType, String target, List<ScanPlan> plans) {
            String taskId = "scan_" + System.currentTimeMillis();
            List<Map<String, Object>> summaries = plans.stream().map(ScanPlan::toSummary).collect(Collectors.toList());
            ScanState state = new ScanState(taskId, scanType, target, STATUS_RUNNING, "准备扫描目标接口", plans, summaries, LocalDateTime.now());
            state.lastMessage = "扫描任务初始化完成";
            return state;
        }

        boolean hasTask() {
            return taskId != null;
        }

        boolean isActive() {
            return STATUS_RUNNING.equals(status) || STATUS_PAUSED.equals(status);
        }

        int getProgressPercent() {
            if (totalInterfaces <= 0) {
                return 0;
            }
            return Math.min(100, (int) Math.round(completedInterfaces * 100.0 / totalInterfaces));
        }

        long getDurationSeconds() {
            if (startTime == null) {
                return 0L;
            }
            LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
            return Math.max(0L, java.time.Duration.between(startTime, end).getSeconds());
        }

        List<Map<String, Object>> getSortedResults() {
            synchronized (results) {
                return results.stream()
                        .sorted(Comparator.comparing(item -> Objects.toString(item.get("detectedAt"), ""), Comparator.reverseOrder()))
                        .collect(Collectors.toCollection(LinkedList::new));
            }
        }
    }
}
