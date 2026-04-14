package com.network.monitor.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.common.constant.VerifyStatusConstant;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.entity.PayloadLibraryEntity;
import com.network.monitor.entity.ScanHistoryEntity;
import com.network.monitor.entity.ScanInterfaceEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.entity.VulnerabilityRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.mapper.VulnerabilityRuleMapper;
import com.network.monitor.service.PayloadLibraryService;
import com.network.monitor.service.ScanHistoryService;
import com.network.monitor.service.ScanInterfaceService;
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

import javax.annotation.PostConstruct;
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
    
    private static final String SCAN_TRAFFIC_HEADER = "X-Scan-Traffic";
    private static final String SCAN_TRAFFIC_VALUE = "vulnerability-scan";
    private static final String SCAN_SOURCE_HEADER = "X-Scan-Source";
    private static final String SCAN_SOURCE_VALUE = "monitor-service";

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
    private ScanInterfaceService scanInterfaceService;

    @Autowired
    private PayloadLibraryService payloadLibraryService;

    @Autowired
    private ScanHistoryService scanHistoryService;

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Autowired
    private VulnerabilityRuleMapper vulnerabilityRuleMapper;

    @Autowired
    private VulnerabilityMonitorMapper vulnerabilityMonitorMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "vuln-scan-executor");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();
    private final Deque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();

    private volatile ScanState currentState = ScanState.idle();

    @PostConstruct
    public void init() {
        loadHistoryFromDatabase();
    }

    private void loadHistoryFromDatabase() {
        try {
            List<ScanHistoryEntity> dbHistory = scanHistoryService.getRecent(historySize);
            for (ScanHistoryEntity entity : dbHistory) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("taskId", entity.getTaskId());
                summary.put("status", entity.getStatus());
                summary.put("scanType", entity.getScanType());
                summary.put("target", entity.getTarget());
                summary.put("discoveredCount", entity.getDiscoveredCount());
                summary.put("completedInterfaces", entity.getCompletedInterfaces());
                summary.put("totalInterfaces", entity.getTotalInterfaces());
                summary.put("startTime", formatTime(entity.getStartTime()));
                summary.put("endTime", formatTime(entity.getEndTime()));
                summary.put("durationSeconds", entity.getDurationSeconds());
                summary.put("summary", entity.getSummary());
                history.addLast(summary);
            }
            log.info("从数据库加载扫描历史：{} 条记录", history.size());
        } catch (Exception e) {
            log.warn("从数据库加载扫描历史失败：{}", e.getMessage());
        }
    }

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

    @Override
    public Map<String, Object> getScanInterfaces(String scanType) {
        String normalizedType = normalizeScanType(scanType);
        List<ScanPlan> plans = detectInterfaces(normalizedType);
        List<Map<String, Object>> interfaces = plans.stream()
                .map(ScanPlan::toSummary)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanType", normalizedType);
        result.put("totalInterfaces", interfaces.size());
        result.put("interfaces", interfaces);
        return result;
    }

    @Override
    public Map<String, Object> startCustomScan(List<Long> interfaceIds) {
        synchronized (stateLock) {
            if (currentState.isActive()) {
                return buildStateResponse(currentState, "当前已有扫描任务正在执行，请先暂停或终止后再试");
            }

            if (interfaceIds == null || interfaceIds.isEmpty()) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("success", false);
                errorResult.put("message", "请至少选择一个接口进行扫描");
                return errorResult;
            }

            List<ScanInterfaceEntity> interfaces = scanInterfaceService.getByIds(interfaceIds);
            if (interfaces.isEmpty()) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("success", false);
                errorResult.put("message", "未找到有效的扫描接口");
                return errorResult;
            }

            List<ScanPlan> plans = interfaces.stream()
                    .map(this::createScanPlanFromEntity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (plans.isEmpty()) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("success", false);
                errorResult.put("message", "无法创建有效的扫描计划");
                return errorResult;
            }

            ScanState state = ScanState.start("CUSTOM", targetDisplayUrl, plans);
            currentState = state;

            executorService.submit(() -> executeScan(state));
            log.info("自定义扫描任务已启动：taskId={}, interfaceIds={}, totalInterfaces={}",
                    state.taskId, interfaceIds, state.totalInterfaces);
            return buildStateResponse(state, "自定义扫描任务已启动");
        }
    }

    @Override
    public Map<String, Object> getSelectableInterfaces(String vulnType, String riskLevel, Long targetId) {
        List<ScanInterfaceEntity> all = scanInterfaceService.getAllEnabled();
        
        List<ScanInterfaceEntity> filtered = all.stream()
                .filter(e -> vulnType == null || vulnType.isEmpty() || containsVulnType(e.getVulnType(), vulnType))
                .filter(e -> riskLevel == null || riskLevel.isEmpty() || riskLevel.equals(e.getRiskLevel()))
                .filter(e -> targetId == null || targetId.equals(e.getTargetId()))
                .collect(Collectors.toList());
        
        List<Map<String, Object>> interfaceList = filtered.stream()
                .map(this::toSelectableInterface)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", filtered.size());
        result.put("interfaces", interfaceList);
        return result;
    }

    private ScanPlan createScanPlanFromEntity(ScanInterfaceEntity entity) {
        String vulnType = entity.getVulnType();
        String path = entity.getInterfacePath();
        String method = entity.getHttpMethod();
        String riskLevel = entity.getRiskLevel();
        String title = entity.getInterfaceName();
        
        return switch (vulnType) {
            case "SQL_INJECTION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    () -> probeSqlInjection("FULL"));
            case "XSS" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    () -> probeXssByPath(path, "FULL"));
            case "COMMAND_INJECTION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    () -> probeCommandInjection("FULL"));
            case "PATH_TRAVERSAL" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    () -> probePathTraversal("FULL"));
            case "FILE_INCLUSION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeFileInclude);
            case "SSRF" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeSsrf);
            case "XXE" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeXxe);
            case "CSRF" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeCsrf);
            case "DESERIALIZATION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeDeserialization);
            case "DDOS" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                    this::probeDdos);
            default -> {
                log.warn("不支持的漏洞类型：{}", vulnType);
                yield null;
            }
        };
    }

    private List<ScanFinding> toList(ScanFinding finding) {
        if (finding == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(finding);
    }

    private List<ScanFinding> probeXssByPath(String path, String scanType) {
        if (path.contains("submit-comment") || path.contains("comment")) {
            return probeStoredXss(path, scanType);
        } else if (path.contains("search")) {
            return probeReflectiveXss(scanType);
        } else if (path.contains("profile")) {
            return probeDomXss(scanType);
        }
        return Collections.emptyList();
    }

    private List<ScanFinding> probeStoredXss(String path, String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = XssPayload.getPayloads(scanType);
        
        for (PayloadCase payload : payloads) {
            ProbeResponse response = doPostForm(path, Map.of("content", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "评论提交成功", "存储型XSS漏洞")) {
                Long ruleId = determineRuleId("XSS", payload.payload(), path);
                findings.add(buildFinding("XSS 漏洞 - 存储型评论接口", "XSS", "HIGH",
                        path, "POST", payload.payload(), payload.matchKeyword(),
                        "评论内容未过滤直接存入数据库，后续查询时会在前端执行恶意脚本", response.body,
                        defaultFixSuggestion("XSS", path), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeDeserialization() {
        List<ScanFinding> findings = new ArrayList<>();
        try {
            ProbeResponse genResponse = doGet("/target/deserial/generate-test-data", Collections.emptyMap());
            if (genResponse.isBlocked()) {
                return findings;
            }
            
            Map<String, Object> genBody = parseBody(genResponse.body);
            Map<String, Object> genData = getNestedMap(genBody, "data");
            Map<String, Object> testData = getNestedMap(genData, "test_data");
            String serializedBase64 = Objects.toString(testData.get("serialized_base64"), "");
            
            if (serializedBase64.isBlank()) {
                return findings;
            }
            
            ProbeResponse response = doPostText("/target/deserial/parse", serializedBase64, MediaType.TEXT_PLAIN);
            if (response.isBlocked()) {
                return findings;
            }
            
            if (containsAny(response.body, "反序列化漏洞", "反序列化成功", "deserialized_object")) {
                findings.add(buildFinding("Java反序列化漏洞 - 对象解析接口", "DESERIALIZATION", "CRITICAL",
                        "/target/deserial/parse", "POST", "Base64序列化对象", "deserialized_object",
                        "未对反序列化类进行白名单校验，可能导致远程代码执行", response.body,
                        defaultFixSuggestion("DESERIALIZATION", "/target/deserial/parse"), 53L));
            }
        } catch (Exception e) {
            log.warn("反序列化扫描失败: {}", e.getMessage());
        }
        return findings;
    }

    private List<ScanFinding> probeDdos() {
        List<ScanFinding> findings = new ArrayList<>();
        
        ProbeResponse cpuResponse = doGet("/target/ddos/compute-heavy", Collections.emptyMap());
        if (!cpuResponse.isBlocked() && containsAny(cpuResponse.body, "DDoS攻击目标", "CPU密集型计算完成")) {
            findings.add(buildFinding("DDoS攻击目标 - CPU密集型接口", "DDOS", "HIGH",
                    "/target/ddos/compute-heavy", "GET", "N/A", "CPU密集型计算完成",
                    "CPU密集型计算接口易受DDoS攻击，高频请求可耗尽服务器资源", cpuResponse.body,
                    defaultFixSuggestion("DDOS", "/target/ddos/compute-heavy"), 60L));
        }
        
        ProbeResponse ioResponse = doGet("/target/ddos/io-delay", Map.of("delay", "100"));
        if (!ioResponse.isBlocked() && containsAny(ioResponse.body, "DDoS攻击目标", "I/O操作模拟完成")) {
            findings.add(buildFinding("DDoS攻击目标 - I/O延迟型接口", "DDOS", "MEDIUM",
                    "/target/ddos/io-delay", "GET", "delay=100", "I/O操作模拟完成",
                    "I/O延迟接口易受慢速攻击，可长期占用连接资源", ioResponse.body,
                    defaultFixSuggestion("DDOS", "/target/ddos/io-delay"), 60L));
        }
        
        ProbeResponse pingResponse = doGet("/target/ddos/ping", Collections.emptyMap());
        if (!pingResponse.isBlocked() && containsAny(pingResponse.body, "pong", "total_requests")) {
            findings.add(buildFinding("DDoS攻击目标 - Ping洪水接口", "DDOS", "MEDIUM",
                    "/target/ddos/ping", "GET", "N/A", "pong",
                    "简单Ping接口易受高频洪水攻击，可冲击网络栈", pingResponse.body,
                    defaultFixSuggestion("DDOS", "/target/ddos/ping"), 60L));
        }
        
        return findings;
    }

    private Map<String, Object> toSelectableInterface(ScanInterfaceEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("interfaceName", entity.getInterfaceName());
        map.put("interfacePath", entity.getInterfacePath());
        map.put("httpMethod", entity.getHttpMethod());
        map.put("vulnType", entity.getVulnType());
        map.put("riskLevel", entity.getRiskLevel());
        map.put("targetId", entity.getTargetId());
        map.put("defenseRuleStatus", entity.getDefenseRuleStatus());
        map.put("enabled", entity.getEnabled());
        return map;
    }

    private boolean containsVulnType(String entityVulnType, String filterVulnType) {
        if (entityVulnType == null || filterVulnType == null) {
            return false;
        }
        return entityVulnType.equalsIgnoreCase(filterVulnType);
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
                    List<ScanFinding> findings = plan.operation.execute();
                    if (findings != null && !findings.isEmpty()) {
                        for (ScanFinding finding : findings) {
                            Map<String, Object> resultItem = finding.toMap();
                            autoInsertVuln(state, resultItem);
                            state.results.add(resultItem);
                        }
                        state.discoveredCount = state.results.size();
                    }
                } catch (Exception ex) {
                    log.warn("扫描接口失败：path={}, msg={}", plan.path, ex.getMessage());
                    
                    if (isDefenseRuleBlock(ex.getMessage())) {
                        markInterfaceAsDefended(plan.path);
                        ScanFinding defenseFinding = createDefenseRuleFinding(
                            plan.title, plan.vulnType, plan.riskLevel, plan.path,
                            "扫描请求被网关拦截，可能已配置防御规则"
                        );
                        Map<String, Object> resultItem = defenseFinding.toMap();
                        state.results.add(resultItem);
                        state.discoveredCount = state.results.size();
                    } else {
                        state.warnings.add(plan.path + " 扫描失败：" + ex.getMessage());
                    }
                } finally {
                    state.completedInterfaces++;
                }
            }

            if (STATUS_TERMINATED.equals(state.status)) {
                finalizeTerminated(state);
                return;
            }

            state.currentStep = "汇总扫描结果";
            state.summary = buildSummary(state);
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
        
        try {
            List<ScanInterfaceEntity> dbInterfaces = scanInterfaceService.getAllEnabled();
            
            if (!dbInterfaces.isEmpty()) {
                log.info("从数据库加载扫描接口配置：共{}个接口", dbInterfaces.size());
                
                for (ScanInterfaceEntity scanInterface : dbInterfaces) {
                    if (!isFullScan(scanType) && !isQuickScanType(scanType, scanInterface.getVulnType())) {
                        continue;
                    }
                    
                    ScanPlan plan = createScanPlanFromConfig(scanInterface, scanType);
                    if (plan != null) {
                        plans.add(plan);
                    }
                }
                
                if (!plans.isEmpty()) {
                    plans.sort(Comparator.comparingInt(p -> getPriorityByVulnType(p.vulnType())));
                    return plans;
                }
            }
        } catch (Exception e) {
            log.warn("从数据库加载扫描接口配置失败，使用默认配置：{}", e.getMessage());
        }
        
        log.info("使用默认硬编码扫描接口配置");
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

    private ScanPlan createScanPlanFromConfig(ScanInterfaceEntity scanInterface, String scanType) {
        try {
            String vulnType = scanInterface.getVulnType();
            String path = scanInterface.getInterfacePath();
            String method = scanInterface.getHttpMethod();
            String riskLevel = scanInterface.getRiskLevel();
            String title = scanInterface.getInterfaceName();
            
            return switch (vulnType) {
                case "SQL_INJECTION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        () -> probeSqlInjection(scanType));
                case "XSS" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        () -> probeXssByPath(path, scanType));
                case "COMMAND_INJECTION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        () -> probeCommandInjection(scanType));
                case "PATH_TRAVERSAL" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        () -> probePathTraversal(scanType));
                case "FILE_INCLUSION" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        this::probeFileInclude);
                case "SSRF" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        this::probeSsrf);
                case "XXE" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        this::probeXxe);
                case "CSRF" -> new ScanPlan(title, vulnType, riskLevel, method, path,
                        this::probeCsrf);
                default -> {
                    log.warn("不支持的漏洞类型：{}", vulnType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("创建扫描计划失败：interfaceId={}, error={}", scanInterface.getId(), e.getMessage());
            return null;
        }
    }

    private ScanFinding createDefenseRuleFinding(String title, String vulnType, String riskLevel, String path, String defenseRuleNote) {
        return new ScanFinding(
            title + " - 已配置防御规则",
            vulnType,
            riskLevel,
            path,
            "N/A",
            "N/A",
            "已配置防御规则",
            defenseRuleNote != null ? defenseRuleNote : "该接口已配置攻击检测规则，扫描请求会被网关拦截，无需再次扫描",
            "该接口已纳入安全防护体系，网关会自动拦截攻击请求",
            "防御规则已生效，建议定期检查规则有效性",
            "已配置防御规则，跳过扫描",
            null
        );
    }

    private boolean isDefenseRuleBlock(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String msg = errorMessage.toLowerCase(Locale.ROOT);
        return msg.contains("400") || 
               msg.contains("403") ||
               msg.contains("bad request") ||
               msg.contains("forbidden") ||
               msg.contains("blocked") ||
               msg.contains("拦截") ||
               msg.contains("invalid request") ||
               msg.contains("access denied");
    }

    private void markInterfaceAsDefended(String path) {
        try {
            List<ScanInterfaceEntity> interfaces = scanInterfaceService.getAllEnabled();
            for (ScanInterfaceEntity entity : interfaces) {
                if (path.equals(entity.getInterfacePath())) {
                    if (entity.getDefenseRuleStatus() == null || entity.getDefenseRuleStatus() == 0) {
                        entity.setDefenseRuleStatus(2);
                        entity.setDefenseRuleNote("系统自动检测：扫描请求被网关拦截");
                        scanInterfaceService.update(entity);
                        log.info("自动标记接口为已配置防御规则：path={}", path);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("标记接口防御规则失败：path={}, error={}", path, e.getMessage());
        }
    }

    private boolean isQuickScanType(String scanType, String vulnType) {
        return switch (scanType.toUpperCase(Locale.ROOT)) {
            case "QUICK" -> List.of("SQL_INJECTION", "XSS", "COMMAND_INJECTION", "PATH_TRAVERSAL")
                    .contains(vulnType);
            case "SQL_INJECTION" -> "SQL_INJECTION".equals(vulnType);
            case "XSS" -> "XSS".equals(vulnType);
            case "COMMAND_INJECTION" -> "COMMAND_INJECTION".equals(vulnType);
            case "PATH_TRAVERSAL" -> "PATH_TRAVERSAL".equals(vulnType);
            default -> true;
        };
    }

    private int getPriorityByVulnType(String vulnType) {
        return switch (vulnType) {
            case "COMMAND_INJECTION" -> 1;
            case "SQL_INJECTION" -> 2;
            case "PATH_TRAVERSAL" -> 3;
            case "XSS" -> 4;
            case "FILE_INCLUSION" -> 5;
            case "SSRF" -> 6;
            case "XXE" -> 7;
            case "CSRF" -> 8;
            default -> 9;
        };
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

    private List<ScanFinding> probeSqlInjection(String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = SqlInjectionPayload.getPayloads(scanType);
        List<PayloadCase> mergedPayloads = payloads;

        for (PayloadCase payload : mergedPayloads) {
            ProbeResponse response = doGet("/target/sql/query", Map.of("id", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "SQL注入漏洞", "多语句执行成功", "executed_sql")) {
                Long ruleId = determineRuleId("SQL_INJECTION", payload.payload(), "/target/sql/query");
                findings.add(buildFinding("SQL 注入漏洞 - 用户查询接口", "SQL_INJECTION", "HIGH",
                        "/target/sql/query", "GET", payload.payload(), payload.matchKeyword(),
                        "发现未经参数化处理的 SQL 查询，可被构造恒真条件或堆叠语句绕过。", response.body,
                        defaultFixSuggestion("SQL_INJECTION", "/target/sql/query"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeReflectiveXss(String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = XssPayload.getPayloads(scanType);
        
        for (PayloadCase payload : payloads) {
            ProbeResponse response = doGet("/target/xss/search", Map.of("keyword", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "反射型XSS漏洞", "搜索成功")) {
                Long ruleId = determineRuleId("XSS", payload.payload(), "/target/xss/search");
                findings.add(buildFinding("XSS 漏洞 - 反射型搜索接口", "XSS", "MEDIUM",
                        "/target/xss/search", "GET", payload.payload(), payload.matchKeyword(),
                        "搜索关键词被直接拼接到响应中，前端渲染时可能执行恶意脚本。", response.body,
                        defaultFixSuggestion("XSS", "/target/xss/search"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeDomXss(String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = XssPayload.getPayloads(scanType);
        
        for (PayloadCase payload : payloads) {
            ProbeResponse response = doGet("/target/xss/profile", Map.of("username", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "DOM型XSS漏洞", "获取资料成功")) {
                Long ruleId = determineRuleId("XSS", payload.payload(), "/target/xss/profile");
                findings.add(buildFinding("XSS 漏洞 - DOM 资料渲染接口", "XSS", "MEDIUM",
                        "/target/xss/profile", "GET", payload.payload(), payload.matchKeyword(),
                        "后端返回未转义的 HTML 片段，前端若直接使用 innerHTML 渲染将触发脚本执行。", response.body,
                        defaultFixSuggestion("XSS", "/target/xss/profile"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeCommandInjection(String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = CommandInjectionPayload.getPayloads(scanType);
        
        for (PayloadCase payload : payloads) {
            ProbeResponse response = doGet("/target/cmd/execute", Map.of("cmd", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "命令执行结果", "纯命令注入漏洞触发成功")) {
                Long ruleId = determineRuleId("COMMAND_INJECTION", payload.payload(), "/target/cmd/execute");
                findings.add(buildFinding("命令注入漏洞 - 系统命令执行接口", "COMMAND_INJECTION", "CRITICAL",
                        "/target/cmd/execute", "GET", payload.payload(), payload.matchKeyword(),
                        "用户输入被直接拼接为系统命令执行，具备远程命令执行风险。", response.body,
                        defaultFixSuggestion("COMMAND_INJECTION", "/target/cmd/execute"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probePathTraversal(String scanType) {
        List<ScanFinding> findings = new ArrayList<>();
        List<PayloadCase> payloads = PathTraversalPayload.getPayloads(scanType);
        
        for (PayloadCase payload : payloads) {
            ProbeResponse response = doGet("/target/path/read", Map.of("filename", payload.payload()));
            if (response.isBlocked()) {
                continue;
            }
            if (containsIgnoreCase(response.body, payload.matchKeyword())
                    && containsAny(response.body, "路径遍历漏洞", "文件读取成功")) {
                Long ruleId = determineRuleId("PATH_TRAVERSAL", payload.payload(), "/target/path/read");
                findings.add(buildFinding("路径遍历漏洞 - 文件读取接口", "PATH_TRAVERSAL", "HIGH",
                        "/target/path/read", "GET", payload.payload(), payload.matchKeyword(),
                        "文件名未经严格约束即参与路径拼接，可读取项目资源目录之外的敏感文件。", response.body,
                        defaultFixSuggestion("PATH_TRAVERSAL", "/target/path/read"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeFileInclude() {
        List<ScanFinding> findings = new ArrayList<>();
        List<String> payloads = List.of(
            "config/test.properties",
            "config/application.yml",
            "WEB-INF/web.xml",
            "../config/database.properties"
        );
        
        for (String payload : payloads) {
            ProbeResponse response = doGet("/target/file/include", Map.of("path", payload));
            if (response.isBlocked()) {
                continue;
            }
            if (containsAny(response.body, "文件包含漏洞", "db.password", "文件加载成功", "application")) {
                Long ruleId = determineRuleId("FILE_INCLUSION", payload, "/target/file/include");
                findings.add(buildFinding("文件包含漏洞 - 动态资源加载接口", "FILE_INCLUSION", "HIGH",
                        "/target/file/include", "GET", payload, "敏感配置内容",
                        "用户可控制待加载资源路径，导致本地配置文件和敏感内容被直接回显。", response.body,
                        defaultFixSuggestion("FILE_INCLUSION", "/target/file/include"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeSsrf() {
        List<ScanFinding> findings = new ArrayList<>();
        List<String> payloads = List.of(
            gatewayBaseUrl + "/target/ddos/status",
            "http://127.0.0.1:9001/target/ddos/status",
            "http://localhost:9001/target/ddos/status",
            "file:///etc/passwd"
        );
        
        for (String payload : payloads) {
            ProbeResponse response = doGet("/target/ssrf/request", Map.of("url", payload));
            if (response.isBlocked()) {
                continue;
            }
            if (containsAny(response.body, "SSRF漏洞", "DDoS被攻击目标状态", "请求成功（漏洞接口）", "root:")) {
                Long ruleId = determineRuleId("SSRF", payload, "/target/ssrf/request");
                findings.add(buildFinding("SSRF 漏洞 - 服务端请求转发接口", "SSRF", "HIGH",
                        "/target/ssrf/request", "GET", payload, "内部服务响应",
                        "服务端对外部 URL 无约束访问，可被用来探测内网或读取受信任服务响应。", response.body,
                        defaultFixSuggestion("SSRF", "/target/ssrf/request"), ruleId));
            }
        }
        return findings;
    }

    private List<ScanFinding> probeXxe() {
        List<ScanFinding> findings = new ArrayList<>();
        try {
            ProbeResponse cases = doGet("/target/xxe/test-cases", Collections.emptyMap());
            if (cases.isBlocked()) {
                return findings;
            }
            Map<String, Object> caseBody = parseBody(cases.body);
            Map<String, Object> caseData = getNestedMap(caseBody, "data");
            Map<String, Object> testCases = getNestedMap(caseData, "test_cases");
            
            List<String> xxePayloads = List.of(
                Objects.toString(testCases.get("xxe_project_config"), ""),
                Objects.toString(testCases.get("xxe_etc_passwd"), ""),
                Objects.toString(testCases.get("xxe_simple"), "")
            );
            
            for (String xmlPayload : xxePayloads) {
                if (xmlPayload.isBlank()) {
                    continue;
                }
                ProbeResponse response = doPostText("/target/xxe/parse", xmlPayload, MediaType.APPLICATION_XML);
                if (response.isBlocked()) {
                    continue;
                }
                if (containsAny(response.body, "XXE漏洞", "has_external_entity", "test_password_123", "root:")) {
                    findings.add(buildFinding("XXE 漏洞 - XML 外部实体解析接口", "XXE", "HIGH",
                            "/target/xxe/parse", "POST", "<!DOCTYPE ...>", "外部实体解析成功",
                            "XML 解析器未禁用外部实体，攻击者可借此读取项目内文件内容。", response.body,
                            defaultFixSuggestion("XXE", "/target/xxe/parse"), 47L));
                }
            }
        } catch (Exception e) {
            log.warn("XXE扫描失败: {}", e.getMessage());
        }
        return findings;
    }

    private List<ScanFinding> probeCsrf() {
        List<ScanFinding> findings = new ArrayList<>();
        try {
            List<String> nicknames = List.of("scan_csrf_user", "csrf_test_123", "attacker_name");
            
            for (String nickname : nicknames) {
                ProbeResponse response = doPostForm("/target/csrf/update-name", Map.of(
                        "userId", "1",
                        "nickname", nickname
                ));
                if (response.isBlocked()) {
                    continue;
                }
                if (containsAny(response.body, "CSRF漏洞", nickname, "昵称修改成功（漏洞接口）")) {
                    findings.add(buildFinding("CSRF 漏洞 - 用户昵称修改接口", "CSRF", "MEDIUM",
                            "/target/csrf/update-name", "POST", nickname, nickname,
                            "状态修改接口缺少 CSRF Token 校验，可被第三方站点诱导发起跨站请求。", response.body,
                            defaultFixSuggestion("CSRF", "/target/csrf/update-name"), 57L));
                }
            }
        } catch (Exception e) {
            log.warn("CSRF扫描失败: {}", e.getMessage());
        }
        return findings;
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
            if (entity.getFixSuggestion() == null || entity.getFixSuggestion().isBlank()) {
                entity.setFixSuggestion(defaultFixSuggestion(entity.getVulnType(), entity.getVulnPath()));
            }
            
            Long ruleId = item.get("ruleId") != null ? Long.valueOf(item.get("ruleId").toString()) : null;
            
            VulnerabilityMonitorEntity existing = findExistingVulnerabilityByRule(entity, ruleId);
            if (existing != null) {
                entity.setId(existing.getId());
                entity.setRuleCount(existing.getRuleCount());
                entity.setRuleIds(existing.getRuleIds());
                entity.setDefenseStatus(existing.getDefenseStatus());
                entity.setAttackCount(existing.getAttackCount() != null ? existing.getAttackCount() + 1 : 1);
                entity.setFirstAttackTime(existing.getFirstAttackTime());
                entity.setLastAttackTime(LocalDateTime.now());
            } else {
                entity.setAttackCount(1);
                entity.setFirstAttackTime(LocalDateTime.now());
                entity.setLastAttackTime(LocalDateTime.now());
                if (ruleId != null) {
                    entity.setRuleIds(String.valueOf(ruleId));
                    entity.setRuleCount(1);
                    entity.setDefenseStatus(2);
                }
            }
            
            VulnerabilityMonitorEntity saved = vulnerabilityVerifyService.saveOrUpdateVulnerability(entity);
            
            if (saved != null && ruleId != null) {
                updateVulnerabilityRuleAssociation(saved, ruleId);
            } else if (saved != null && (saved.getRuleCount() == null || saved.getRuleCount() == 0)) {
                updateVulnerabilityRuleInfo(saved);
            }
            
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
    
    private VulnerabilityMonitorEntity findExistingVulnerabilityByRule(VulnerabilityMonitorEntity entity, Long ruleId) {
        if (ruleId != null) {
            VulnerabilityMonitorEntity existing = vulnerabilityMonitorMapper.selectByRuleId(ruleId);
            if (existing != null) {
                return existing;
            }
        }
        return findExistingVulnerability(entity);
    }
    
    private void updateVulnerabilityRuleAssociation(VulnerabilityMonitorEntity vulnerability, Long ruleId) {
        try {
            MonitorRuleEntity rule = monitorRuleMapper.selectById(ruleId);
            if (rule == null) {
                return;
            }
            
            String existingRuleIds = vulnerability.getRuleIds();
            if (existingRuleIds != null && !existingRuleIds.isEmpty()) {
                if (existingRuleIds.contains(String.valueOf(ruleId))) {
                    return;
                }
                vulnerability.setRuleIds(existingRuleIds + "," + ruleId);
                vulnerability.setRuleCount(vulnerability.getRuleCount() + 1);
            } else {
                vulnerability.setRuleIds(String.valueOf(ruleId));
                vulnerability.setRuleCount(1);
            }
            vulnerability.setDefenseStatus(2);
            
            vulnerabilityMonitorMapper.updateDefenseStatus(
                    vulnerability.getId(),
                    vulnerability.getDefenseStatus(),
                    vulnerability.getRuleCount(),
                    vulnerability.getRuleIds()
            );
            
            VulnerabilityRuleEntity vulnRule = new VulnerabilityRuleEntity();
            vulnRule.setVulnerabilityId(vulnerability.getId());
            vulnRule.setRuleId(rule.getId());
            vulnRule.setRuleName(rule.getRuleName());
            vulnRule.setAttackType(rule.getAttackType());
            vulnRule.setRiskLevel(rule.getRiskLevel());
            
            try {
                vulnerabilityRuleMapper.insert(vulnRule);
            } catch (Exception e) {
                log.debug("规则关联已存在：vulnId={}, ruleId={}", vulnerability.getId(), ruleId);
            }
            
            log.info("更新漏洞规则关联：vulnId={}, vulnName={}, ruleId={}", 
                    vulnerability.getId(), vulnerability.getVulnName(), ruleId);
        } catch (Exception e) {
            log.warn("更新漏洞规则关联失败：vulnId={}, ruleId={}, error={}", vulnerability.getId(), ruleId, e.getMessage());
        }
    }

    private VulnerabilityMonitorEntity findExistingVulnerability(VulnerabilityMonitorEntity entity) {
        if (entity.getVulnName() != null && !entity.getVulnName().isBlank()) {
            VulnerabilityMonitorEntity existing = vulnerabilityMonitorMapper.selectByVulnName(entity.getVulnName());
            if (existing != null) {
                return existing;
            }
        }
        if (entity.getVulnPath() != null && !entity.getVulnPath().isBlank() 
                && entity.getVulnType() != null && !entity.getVulnType().isBlank()) {
            return vulnerabilityMonitorMapper.selectByPathAndType(entity.getVulnPath(), entity.getVulnType());
        }
        return null;
    }

    private void updateVulnerabilityRuleInfo(VulnerabilityMonitorEntity vulnerability) {
        try {
            String vulnType = vulnerability.getVulnType();
            if (vulnType == null || vulnType.isBlank()) {
                return;
            }
            
            List<MonitorRuleEntity> rules = monitorRuleMapper.selectByAttackType(vulnType);
            if (rules == null || rules.isEmpty()) {
                return;
            }
            
            List<Long> ruleIds = rules.stream()
                    .map(MonitorRuleEntity::getId)
                    .collect(Collectors.toList());
            
            String ruleIdsStr = ruleIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            
            vulnerability.setRuleCount(ruleIds.size());
            vulnerability.setRuleIds(ruleIdsStr);
            vulnerability.setDefenseStatus(ruleIds.size() > 0 ? 2 : 0);
            
            vulnerabilityMonitorMapper.updateDefenseStatus(
                    vulnerability.getId(),
                    vulnerability.getDefenseStatus(),
                    vulnerability.getRuleCount(),
                    vulnerability.getRuleIds()
            );
            
            for (MonitorRuleEntity rule : rules) {
                VulnerabilityRuleEntity vulnRule = new VulnerabilityRuleEntity();
                vulnRule.setVulnerabilityId(vulnerability.getId());
                vulnRule.setRuleId(rule.getId());
                vulnRule.setRuleName(rule.getRuleName());
                vulnRule.setAttackType(rule.getAttackType());
                vulnRule.setRiskLevel(rule.getRiskLevel());
                
                try {
                    vulnerabilityRuleMapper.insert(vulnRule);
                } catch (Exception e) {
                    log.debug("规则关联已存在：vulnId={}, ruleId={}", vulnerability.getId(), rule.getId());
                }
            }
            
            log.info("更新漏洞规则关联：vulnId={}, vulnName={}, ruleCount={}", 
                    vulnerability.getId(), vulnerability.getVulnName(), ruleIds.size());
        } catch (Exception e) {
            log.warn("更新漏洞规则关联失败：vulnId={}, error={}", vulnerability.getId(), e.getMessage());
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

    private Long determineRuleId(String vulnType, String payload, String vulnPath) {
        if (vulnType == null) return null;
        
        return switch (vulnType) {
            case "SQL_INJECTION" -> determineSqlInjectionRuleId(payload);
            case "XSS" -> determineXssRuleId(payload, vulnPath);
            case "COMMAND_INJECTION" -> determineCommandInjectionRuleId(payload);
            case "PATH_TRAVERSAL" -> determinePathTraversalRuleId(payload);
            case "FILE_INCLUSION" -> determineFileInclusionRuleId(payload);
            case "SSRF" -> determineSsrfRuleId(payload);
            case "XXE" -> 47L;
            case "DESERIALIZATION" -> 53L;
            case "CSRF" -> 57L;
            case "DDOS" -> determineDdosRuleId(vulnPath);
            default -> null;
        };
    }
    
    private Long determineSqlInjectionRuleId(String payload) {
        if (payload == null) return 1L;
        String lower = payload.toLowerCase();
        if (lower.contains("union") && lower.contains("select")) return 1L;
        if (lower.contains("or") && lower.contains("1=1")) return 2L;
        if (lower.contains("and") && lower.contains("=")) return 3L;
        if (lower.contains("drop")) return 4L;
        if (lower.contains("sleep")) return 5L;
        if (lower.contains("benchmark")) return 6L;
        if (lower.contains("--") || lower.contains("#") || lower.contains("/*")) return 7L;
        if (lower.contains(";")) return 8L;
        if (lower.contains("'")) return 9L;
        return 1L;
    }
    
    private Long determineXssRuleId(String payload, String vulnPath) {
        if (payload == null) return 10L;
        String lower = payload.toLowerCase();
        if (lower.contains("<script")) return 10L;
        if (lower.contains("javascript:")) return 11L;
        if (lower.contains("onerror")) return 12L;
        if (lower.contains("onload")) return 13L;
        if (lower.contains("onclick")) return 14L;
        if (lower.contains("alert")) return 15L;
        if (lower.contains("document")) return 16L;
        if (lower.contains("<img")) return 17L;
        if (lower.contains("<svg")) return 18L;
        if (lower.contains("eval")) return 19L;
        if (lower.contains("<iframe")) return 20L;
        return 10L;
    }
    
    private Long determineCommandInjectionRuleId(String payload) {
        if (payload == null) return 21L;
        String lower = payload.toLowerCase();
        if (lower.contains("|")) return 21L;
        if (lower.contains(";")) return 22L;
        if (lower.contains("`")) return 23L;
        if (lower.contains("$(")) return 24L;
        if (lower.contains("cmd") || lower.contains("powershell")) return 25L;
        if (lower.contains("ping")) return 26L;
        if (lower.contains("whoami")) return 27L;
        if (lower.contains("tasklist")) return 28L;
        if (lower.contains("cat")) return 29L;
        return 21L;
    }
    
    private Long determinePathTraversalRuleId(String payload) {
        if (payload == null) return 30L;
        String lower = payload.toLowerCase();
        if (lower.contains("../") || lower.contains("..\\")) return 30L;
        if (lower.contains("/etc/passwd") || lower.contains("/etc/shadow")) return 31L;
        if (lower.contains("c:") || lower.contains("d:") || lower.contains("windows")) return 32L;
        if (lower.contains("%2e%2e") || lower.contains("%2e%2e/")) return 33L;
        if (lower.contains("%252e")) return 34L;
        if (lower.contains("application") || lower.contains("config") || lower.contains("database")) return 35L;
        return 30L;
    }
    
    private Long determineFileInclusionRuleId(String payload) {
        if (payload == null) return 36L;
        String lower = payload.toLowerCase();
        if (lower.contains("include") || lower.contains("require")) return 36L;
        if (lower.contains("data:")) return 37L;
        if (lower.contains("php:")) return 38L;
        if (lower.contains("file:")) return 39L;
        if (lower.contains("classpath:")) return 40L;
        return 36L;
    }
    
    private Long determineSsrfRuleId(String payload) {
        if (payload == null) return 41L;
        String lower = payload.toLowerCase();
        if (lower.contains("192.168.") || lower.contains("10.") || lower.contains("172.")) return 41L;
        if (lower.contains("file:")) return 42L;
        if (lower.contains("dict:")) return 43L;
        if (lower.contains("gopher:")) return 44L;
        if (lower.contains("169.254") || lower.contains("metadata")) return 45L;
        if (lower.contains("127.0.0.1") || lower.contains("localhost")) return 46L;
        return 41L;
    }
    
    private Long determineDdosRuleId(String vulnPath) {
        if (vulnPath == null) return 60L;
        if (vulnPath.contains("compute-heavy")) return 60L;
        if (vulnPath.contains("io-delay")) return 60L;
        if (vulnPath.contains("ping")) return 60L;
        return 60L;
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
                                     String fixSuggestion,
                                     Long ruleId) {
        String finalLevel = normalizeRiskLevel(null, vulnLevel);
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
                null,
                ruleId
        );
    }

    private ProbeResponse doGet(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(gatewayBaseUrl).path(path);
        params.forEach(builder::queryParam);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set(SCAN_TRAFFIC_HEADER, SCAN_TRAFFIC_VALUE);
        headers.set(SCAN_SOURCE_HEADER, SCAN_SOURCE_VALUE);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build().encode().toUri(), 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class);
            return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return new ProbeResponse(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new RuntimeException("请求超时或连接失败: " + e.getMessage());
        }
    }

    private ProbeResponse doPostForm(String path, Map<String, String> formData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(SCAN_TRAFFIC_HEADER, SCAN_TRAFFIC_VALUE);
        headers.set(SCAN_SOURCE_HEADER, SCAN_SOURCE_VALUE);
        String body = formData.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encodeValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(gatewayBaseUrl + path, entity, String.class);
            return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return new ProbeResponse(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new RuntimeException("请求超时或连接失败: " + e.getMessage());
        }
    }

    private ProbeResponse doPostText(String path, String text, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.set(SCAN_TRAFFIC_HEADER, SCAN_TRAFFIC_VALUE);
        headers.set(SCAN_SOURCE_HEADER, SCAN_SOURCE_VALUE);
        HttpEntity<String> entity = new HttpEntity<>(text, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(gatewayBaseUrl + path, entity, String.class);
            return new ProbeResponse(response.getStatusCodeValue(), response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return new ProbeResponse(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new RuntimeException("请求超时或连接失败: " + e.getMessage());
        }
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
        try {
            ScanHistoryEntity entity = new ScanHistoryEntity();
            entity.setTaskId(state.taskId);
            entity.setScanType(state.scanType);
            entity.setTarget(state.target);
            entity.setStatus(state.status);
            entity.setDiscoveredCount(state.discoveredCount);
            entity.setCompletedInterfaces(state.completedInterfaces);
            entity.setTotalInterfaces(state.totalInterfaces);
            entity.setStartTime(state.startTime);
            entity.setEndTime(state.endTime);
            entity.setDurationSeconds((int) state.getDurationSeconds());
            entity.setSummary(state.summary);
            
            if (state.status.equals(STATUS_FAILED) && state.lastMessage != null) {
                entity.setErrorMessage(state.lastMessage);
            }
            
            scanHistoryService.save(entity);
            log.info("扫描历史已保存到数据库：taskId={}", state.taskId);
        } catch (Exception e) {
            log.error("保存扫描历史失败：taskId={}, error={}", state.taskId, e.getMessage());
        }
        
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

        private boolean isBlocked() {
            return statusCode == 400 || statusCode == 403;
        }
    }

    @FunctionalInterface
    private interface ScanOperation {
        List<ScanFinding> execute();
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
                               String aiVerdict,
                               Long ruleId) {
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
            item.put("ruleId", ruleId);
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
