package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.RuleCache;
import com.network.gateway.cache.VulnerabilityCache;
import com.network.gateway.cache.WhitelistCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.dto.AttackEventDTO;
import com.network.gateway.dto.AttackRuleDTO;
import com.network.gateway.dto.DefenseLogDTO;
import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.util.DefenseLogUtil;
import com.network.gateway.util.DefenseResponseUtil;
import com.network.gateway.util.ServerWebExchangeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 攻击规则检测过滤器
 * 实现两阶段检测：
 * 1. 第一阶段：检测URL、Query参数、请求头、Cookie（无需读取请求体）
 * 2. 第二阶段：如果第一阶段未匹配，读取请求体进行检测
 * 
 * 检测机制：
 * - 基于规则的检测：使用预定义的正则规则匹配攻击模式
 * - 基于漏洞的检测：匹配已知漏洞路径，即使未配置防御规则也能检测
 * 
 * 白名单机制：
 * - 路径白名单：跳过URL路径检测
 * - 请求头白名单：跳过指定请求头的检测
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class AttackRuleFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AttackRuleFilter.class);
    
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private static final List<String> BODY_METHODS = List.of("POST", "PUT", "PATCH");

    private static final List<String> TEXT_CONTENT_TYPES = List.of(
            "text/", "application/json", "application/xml", "application/x-www-form-urlencoded"
    );

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private VulnerabilityCache vulnCache;

    @Autowired
    private WhitelistCache whitelistCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isRuleDetectionEnabled()) {
                return chain.filter(exchange);
            }

            if (shouldSkipDetection(exchange)) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String sourceIp = ServerWebExchangeUtil.extractSourceIp(request);
            String uri = request.getURI().getPath();

            RuleCache.MatchResult ruleResult = detectPhase1(exchange);
            if (ruleResult != null) {
                return handleAttackDetected(exchange, sourceIp, ruleResult, startTime, "Phase1-Rule");
            }

            VulnerabilityCache.VulnMatchResult vulnResult = detectVulnerabilityByPath(uri);
            if (vulnResult != null) {
                handleVulnAlertOnly(exchange, sourceIp, vulnResult, startTime, "Phase1-Vuln");
            }

            if (shouldDetectBody(request)) {
                return detectPhase2(exchange, chain, sourceIp, startTime);
            }

            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("攻击规则检测过程中发生异常", e);
            return chain.filter(exchange);
        }
    }

    private boolean isRuleDetectionEnabled() {
        return configCache.getBoolean("rule.detection.enabled", true);
    }

    private boolean shouldSkipDetection(ServerWebExchange exchange) {
        if (ServerWebExchangeUtil.isHealthCheck(exchange)) {
            return true;
        }

        if (ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
            return true;
        }

        if (ServerWebExchangeUtil.isStaticResource(exchange)) {
            return true;
        }

        return false;
    }

    private RuleCache.MatchResult detectPhase1(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String uri = request.getURI().getPath();

        if (!whitelistCache.isPathWhitelisted(uri)) {
            RuleCache.MatchResult uriResult = ruleCache.matchAll(uri);
            if (uriResult != null) {
                logger.debug("URL路径匹配规则: uri={}, rule={}", uri, uriResult.getRuleName());
                return uriResult;
            }
        } else {
            logger.debug("URL路径在白名单中，跳过检测: uri={}", uri);
        }

        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            RuleCache.MatchResult queryResult = ruleCache.matchAll(queryString);
            if (queryResult != null) {
                logger.debug("Query参数匹配规则: query={}, rule={}", queryString, queryResult.getRuleName());
                return queryResult;
            }
            
            try {
                String decodedQuery = java.net.URLDecoder.decode(queryString, java.nio.charset.StandardCharsets.UTF_8);
                if (!decodedQuery.equals(queryString)) {
                    queryResult = ruleCache.matchAll(decodedQuery);
                    if (queryResult != null) {
                        logger.debug("解码后Query参数匹配规则: query={}, rule={}", decodedQuery, queryResult.getRuleName());
                        return queryResult;
                    }
                }
            } catch (Exception e) {
                logger.debug("Query参数解码失败: {}", e.getMessage());
            }
        }

        Map<String, String> headers = ServerWebExchangeUtil.extractHeaders(request);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue();

            if (whitelistCache.isHeaderWhitelisted(headerName)) {
                continue;
            }

            if (headerValue != null && !headerValue.isEmpty()) {
                RuleCache.MatchResult headerResult = ruleCache.matchAll(headerValue);
                if (headerResult != null) {
                    logger.debug("请求头匹配规则: header={}, rule={}", headerName, headerResult.getRuleName());
                    return headerResult;
                }
            }
        }

        String cookieHeader = request.getHeaders().getFirst("Cookie");
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            RuleCache.MatchResult cookieResult = ruleCache.matchAll(cookieHeader);
            if (cookieResult != null) {
                logger.debug("Cookie匹配规则: rule={}", cookieResult.getRuleName());
                return cookieResult;
            }
        }

        return null;
    }

    private boolean shouldDetectBody(ServerHttpRequest request) {
        String method = request.getMethodValue();
        if (!BODY_METHODS.contains(method)) {
            return false;
        }

        String contentType = ServerWebExchangeUtil.extractContentType(request);
        if (contentType == null || "unknown".equals(contentType)) {
            return false;
        }

        for (String textType : TEXT_CONTENT_TYPES) {
            if (contentType.toLowerCase().contains(textType.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private Mono<Void> detectPhase2(ServerWebExchange exchange, GatewayFilterChain chain,
                                    String sourceIp, long startTime) {
        ServerHttpRequest request = exchange.getRequest();

        Long contentLength = ServerWebExchangeUtil.getContentLength(exchange);
        if (contentLength != null && contentLength > 1024 * 1024) {
            logger.debug("请求体过大，跳过检测: size={}", contentLength);
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .timeout(java.time.Duration.ofSeconds(5))
                .flatMap(dataBuffer -> {
                    try {
                        int readableBytes = dataBuffer.readableByteCount();
                        byte[] bytes = new byte[readableBytes];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        String bodyContent = new String(bytes, StandardCharsets.UTF_8);

                        RuleCache.MatchResult bodyResult = ruleCache.matchAll(bodyContent);
                        if (bodyResult != null) {
                            logger.info("请求体匹配规则: rule={}, 立即返回拦截响应", bodyResult.getRuleName());
                            return handleAttackDetected(exchange, sourceIp, bodyResult, startTime, "Phase2-Rule");
                        }

                        VulnerabilityCache.VulnMatchResult vulnResult = detectVulnerabilityByPath(request.getURI().getPath());
                        if (vulnResult != null) {
                            logger.info("请求体检测阶段匹配漏洞: vuln={}, 只产生告警不拦截", vulnResult.getVulnName());
                            handleVulnAlertOnly(exchange, sourceIp, vulnResult, startTime, "Phase2-Vuln");
                        }

                        ServerHttpRequest newRequest = new ServerHttpRequestDecorator(request) {
                            @Override
                            public HttpHeaders getHeaders() {
                                HttpHeaders headers = new HttpHeaders();
                                headers.putAll(super.getHeaders());
                                headers.setContentLength(bytes.length);
                                return headers;
                            }
                            
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(BUFFER_FACTORY.wrap(bytes));
                            }
                        };
                        
                        return chain.filter(exchange.mutate().request(newRequest).build());

                    } catch (Exception e) {
                        logger.error("请求体检测异常: {}", e.getMessage());
                        return chain.filter(exchange);
                    }
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                    logger.warn("请求体读取超时，跳过检测: uri={}", request.getURI().getPath());
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    logger.debug("请求体为空，直接转发: uri={}", request.getURI().getPath());
                    return chain.filter(exchange);
                }));
    }

    private VulnerabilityCache.VulnMatchResult detectVulnerabilityByPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        if (whitelistCache.isPathWhitelisted(uri)) {
            logger.debug("URL路径在白名单中，跳过漏洞检测: uri={}", uri);
            return null;
        }

        VulnerabilityCache.VulnMatchResult result = vulnCache.matchByPath(uri);
        if (result != null) {
            logger.info("漏洞路径匹配: uri={}, vuln={}, type={}, hasDefenseRule={}", 
                uri, result.getVulnName(), result.getVulnType(), result.hasDefenseRule());
        }
        
        return result;
    }

    private Mono<Void> handleAttackDetected(ServerWebExchange exchange, String sourceIp,
                                            RuleCache.MatchResult matchResult, long startTime, String phase) {
        ServerHttpResponse response = exchange.getResponse();

        AttackRuleDTO rule = matchResult.getMatchedRule();
        String attackType = matchResult.getAttackType();
        String riskLevel = matchResult.getRiskLevel();

        AttackEventDTO attackEvent = buildAttackEventDTO(exchange, sourceIp, null, matchResult);
        String eventId = attackEvent.getEventId();

        final String finalEventId = eventId;
        final String finalAttackType = attackType;
        final String finalRiskLevel = riskLevel;
        
        exchange.getAttributes().put("attack_intercepted", true);

        logger.info("攻击检测拦截: ip={}, type={}, rule={}, phase={}, uri={}, eventId={}, 立即返回拦截响应",
                sourceIp, finalAttackType, rule.getRuleName(), phase, 
                exchange.getRequest().getURI().getPath(), finalEventId);

        Mono.fromRunnable(() -> {
            try {
                defenseClient.pushAttackEvent(attackEvent);
                logger.debug("推送攻击事件成功: eventId={}, ip={}, type={}, rule={}", 
                    finalEventId, sourceIp, finalAttackType, rule.getRuleName());
            } catch (Exception e) {
                logger.error("推送攻击事件失败: {}", e.getMessage());
            }

            try {
                long processingTime = System.currentTimeMillis() - startTime;
                TrafficMonitorDTO trafficDTO = buildTrafficDTO(exchange, sourceIp, finalEventId, finalAttackType, finalRiskLevel, processingTime);
                trafficClient.pushTraffic(trafficDTO);
                logger.debug("推送攻击流量数据成功: eventId={}, ip={}, type={}", 
                    finalEventId, sourceIp, finalAttackType);
            } catch (Exception e) {
                logger.error("推送攻击流量数据失败: {}", e.getMessage());
            }

            DefenseLogDTO logDTO = DefenseLogUtil.buildBlockLog(
                    sourceIp,
                    finalEventId,
                    DefenseResultBO.RiskLevel.valueOf(finalRiskLevel),
                    String.format("攻击规则匹配: %s [%s]", rule.getRuleName(), finalAttackType)
            );
            logDTO.setRequestUri(exchange.getRequest().getURI().getPath());
            logDTO.setHttpMethod(exchange.getRequest().getMethodValue());
            logDTO.setDefenseReason(String.format("规则匹配[%s]: %s", rule.getAttackType(), rule.getRuleName()));
            logDTO.setAttackType(finalAttackType);
            logDTO.setRiskLevel(finalRiskLevel);

            try {
                defenseClient.pushDefenseLog(logDTO);
            } catch (Exception e) {
                logger.error("推送攻击检测日志失败: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        return DefenseResponseUtil.buildMaliciousRequestResponse(response, sourceIp, eventId, riskLevel);
    }

    private void handleVulnAlertOnly(ServerWebExchange exchange, String sourceIp,
                                     VulnerabilityCache.VulnMatchResult vulnResult, 
                                     long startTime, String phase) {
        VulnerabilityCache.VulnerabilityInfo vuln = vulnResult.getFirstVuln();
        String attackType = vulnResult.getVulnType();
        String riskLevel = convertVulnLevelToRiskLevel(vulnResult.getVulnLevel());
        String vulnName = vulnResult.getVulnName();
        Long vulnId = vuln.getId();

        AttackEventDTO attackEvent = buildVulnAttackEventDTO(exchange, sourceIp, vulnResult);
        String eventId = attackEvent.getEventId();

        final String finalEventId = eventId;
        final String finalAttackType = attackType;
        final String finalRiskLevel = riskLevel;
        final String finalVulnName = vulnName;
        final Long finalVulnId = vulnId;
        
        exchange.getAttributes().put("vuln_matched", true);
        exchange.getAttributes().put("vuln_id", vulnId);

        logger.info("漏洞匹配告警(不拦截): ip={}, vulnId={}, vulnName={}, type={}, phase={}, uri={}, eventId={}, hasDefenseRule={}",
                sourceIp, finalVulnId, finalVulnName, finalAttackType, phase, 
                exchange.getRequest().getURI().getPath(), finalEventId, vulnResult.hasDefenseRule());
        
        Mono.fromRunnable(() -> {
            try {
                defenseClient.pushAttackEvent(attackEvent);
                logger.debug("推送漏洞攻击事件成功: eventId={}, ip={}, vulnId={}, vulnName={}", 
                    finalEventId, sourceIp, finalVulnId, finalVulnName);
            } catch (Exception e) {
                logger.error("推送漏洞攻击事件失败: {}", e.getMessage());
            }

            try {
                long processingTime = System.currentTimeMillis() - startTime;
                TrafficMonitorDTO trafficDTO = buildTrafficDTO(exchange, sourceIp, finalEventId, 
                    finalAttackType, finalRiskLevel, processingTime);
                trafficDTO.setAbnormalReason("漏洞路径匹配(仅告警): " + finalVulnName);
                trafficClient.pushTraffic(trafficDTO);
                logger.debug("推送漏洞攻击流量数据成功: eventId={}, ip={}, vulnName={}", 
                    finalEventId, sourceIp, finalVulnName);
            } catch (Exception e) {
                logger.error("推送漏洞攻击流量数据失败: {}", e.getMessage());
            }

            DefenseLogDTO logDTO = DefenseLogUtil.buildAlertLog(
                    sourceIp,
                    finalEventId,
                    DefenseResultBO.RiskLevel.valueOf(finalRiskLevel),
                    String.format("漏洞路径匹配(仅告警): %s [%s]", finalVulnName, finalAttackType)
            );
            logDTO.setRequestUri(exchange.getRequest().getURI().getPath());
            logDTO.setHttpMethod(exchange.getRequest().getMethodValue());
            logDTO.setDefenseReason(String.format("漏洞匹配[id=%d]: %s (未配置防御规则，仅告警)", finalVulnId, finalVulnName));
            logDTO.setAttackType(finalAttackType);
            logDTO.setRiskLevel(finalRiskLevel);

            try {
                defenseClient.pushDefenseLog(logDTO);
            } catch (Exception e) {
                logger.error("推送漏洞告警日志失败: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private String convertVulnLevelToRiskLevel(String vulnLevel) {
        if (vulnLevel == null) {
            return "MEDIUM";
        }
        
        switch (vulnLevel.toUpperCase()) {
            case "CRITICAL":
            case "严重":
                return "CRITICAL";
            case "HIGH":
            case "高危":
                return "HIGH";
            case "MEDIUM":
            case "中危":
                return "MEDIUM";
            case "LOW":
            case "低危":
                return "LOW";
            default:
                return "MEDIUM";
        }
    }

    private AttackEventDTO buildVulnAttackEventDTO(ServerWebExchange exchange, String sourceIp,
                                                   VulnerabilityCache.VulnMatchResult vulnResult) {
        ServerHttpRequest request = exchange.getRequest();
        VulnerabilityCache.VulnerabilityInfo vuln = vulnResult.getFirstVuln();
        
        String riskLevel = convertVulnLevelToRiskLevel(vulnResult.getVulnLevel());
        int confidence = calculateVulnConfidence(vulnResult);
        
        AttackEventDTO eventDTO = new AttackEventDTO(
            sourceIp, 
            vulnResult.getVulnType(), 
            riskLevel, 
            confidence
        );
        
        eventDTO.setTargetUri(request.getURI().getPath());
        eventDTO.setHttpMethod(request.getMethodValue());
        
        eventDTO.setDescription(String.format("漏洞路径匹配: %s (ID: %d, 类型: %s)", 
            vulnResult.getVulnName(), vuln.getId(), vulnResult.getVulnType()));
        
        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            Map<String, String> queryParams = new HashMap<>();
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        queryParams.put(key, value);
                    } catch (Exception e) {
                        logger.warn("URL参数解码失败: pair={}, error={}", pair, e.getMessage());
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                } else if (keyValue.length == 1) {
                    queryParams.put(keyValue[0], "");
                }
            }
            eventDTO.setQueryParams(queryParams);
            
            String attackContent = buildAttackContent(queryParams);
            eventDTO.setAttackContent(attackContent);
        }
        
        Map<String, String> headers = ServerWebExchangeUtil.extractHeaders(request);
        eventDTO.setRequestHeaders(headers);
        eventDTO.setUserAgent(headers.get("User-Agent"));
        
        return eventDTO;
    }

    private int calculateVulnConfidence(VulnerabilityCache.VulnMatchResult vulnResult) {
        int baseConfidence = 80;
        
        String vulnLevel = vulnResult.getVulnLevel();
        if (vulnLevel != null) {
            switch (vulnLevel.toUpperCase()) {
                case "CRITICAL":
                case "严重":
                    baseConfidence = 95;
                    break;
                case "HIGH":
                case "高危":
                    baseConfidence = 90;
                    break;
                case "MEDIUM":
                case "中危":
                    baseConfidence = 80;
                    break;
                case "LOW":
                case "低危":
                    baseConfidence = 70;
                    break;
            }
        }
        
        if (vulnResult.hasDefenseRule()) {
            baseConfidence = Math.min(baseConfidence + 5, 99);
        }
        
        return baseConfidence;
    }
    
    private TrafficMonitorDTO buildTrafficDTO(ServerWebExchange exchange, String sourceIp, 
                                              String eventId, String attackType, String riskLevel, 
                                              long processingTime) {
        ServerHttpRequest request = exchange.getRequest();
        
        TrafficMonitorDTO trafficDTO = new TrafficMonitorDTO();
        trafficDTO.setRequestId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        trafficDTO.setEventId(eventId);
        trafficDTO.setSourceIp(sourceIp);
        trafficDTO.setTargetIp(ServerWebExchangeUtil.extractTargetIp(request));
        trafficDTO.setSourcePort(ServerWebExchangeUtil.extractSourcePort(request));
        trafficDTO.setTargetPort(ServerWebExchangeUtil.extractTargetPort(request));
        trafficDTO.setHttpMethod(request.getMethodValue());
        trafficDTO.setProtocol(ServerWebExchangeUtil.extractProtocol(request));
        trafficDTO.setRequestUri(request.getURI().getPath());
        trafficDTO.setRequestTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        trafficDTO.setResponseStatus(400);
        trafficDTO.setResponseTime(processingTime);
        trafficDTO.setAvgProcessingTime(processingTime);
        trafficDTO.setStateTag("攻击");
        trafficDTO.setAbnormalTraffic(true);
        trafficDTO.setAbnormalReason("攻击规则匹配: " + attackType);
        
        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            Map<String, String> queryParams = new HashMap<>();
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        queryParams.put(key, value);
                    } catch (Exception e) {
                        logger.warn("URL参数解码失败: pair={}, error={}", pair, e.getMessage());
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                } else if (keyValue.length == 1) {
                    queryParams.put(keyValue[0], "");
                }
            }
            trafficDTO.setQueryParams(queryParams);
        }
        
        Map<String, String> headers = ServerWebExchangeUtil.extractHeaders(request);
        trafficDTO.setRequestHeaders(headers);
        trafficDTO.setUserAgent(headers.get("User-Agent"));
        
        String contentType = headers.get("Content-Type");
        trafficDTO.setContentType(contentType != null ? contentType : "unknown");
        
        trafficDTO.setCookie(headers.get("Cookie"));
        
        return trafficDTO;
    }
    
    private AttackEventDTO buildAttackEventDTO(ServerWebExchange exchange, String sourceIp, 
                                               String eventId, RuleCache.MatchResult matchResult) {
        ServerHttpRequest request = exchange.getRequest();
        AttackRuleDTO rule = matchResult.getMatchedRule();
        
        int confidence = calculateConfidence(matchResult.getRiskLevel());
        
        AttackEventDTO eventDTO = new AttackEventDTO(
            sourceIp, 
            matchResult.getAttackType(), 
            matchResult.getRiskLevel(), 
            confidence
        );
        
        if (eventId != null && !eventId.isEmpty()) {
            eventDTO.setEventId(eventId);
        }
        
        eventDTO.setRuleName(rule.getRuleName());
        eventDTO.setRuleId(String.valueOf(rule.getId()));
        eventDTO.setTargetUri(request.getURI().getPath());
        eventDTO.setHttpMethod(request.getMethodValue());
        
        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            Map<String, String> queryParams = new HashMap<>();
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        queryParams.put(key, value);
                        logger.debug("URL参数解码成功: key={}, value={}", key, value);
                    } catch (Exception e) {
                        logger.warn("URL参数解码失败: pair={}, error={}", pair, e.getMessage());
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                } else if (keyValue.length == 1) {
                    queryParams.put(keyValue[0], "");
                }
            }
            eventDTO.setQueryParams(queryParams);
            
            String attackContent = buildAttackContent(queryParams);
            eventDTO.setAttackContent(attackContent);
            logger.debug("构建攻击内容: attackContent={}", attackContent);
        }
        
        Map<String, String> headers = ServerWebExchangeUtil.extractHeaders(request);
        eventDTO.setRequestHeaders(headers);
        eventDTO.setUserAgent(headers.get("User-Agent"));
        
        eventDTO.updateDescription();
        
        return eventDTO;
    }
    
    private String buildAttackContent(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private int calculateConfidence(String riskLevel) {
        if (riskLevel == null) {
            return 70;
        }
        
        switch (riskLevel.toUpperCase()) {
            case "CRITICAL":
                return 95;
            case "HIGH":
                return 85;
            case "MEDIUM":
                return 70;
            case "LOW":
                return 50;
            default:
                return 70;
        }
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.ATTACK_RULE_FILTER_ORDER;
    }

    public String getFilterName() {
        return "AttackRuleFilter";
    }

    public String getStatistics() {
        return String.format("攻击规则检测过滤器统计:\n  - %s\n  - %s", 
            ruleCache.getStats(), vulnCache.getStats());
    }

    public int getRuleCount() {
        return ruleCache.size();
    }

    public int getTypeCount() {
        return ruleCache.getTypeCount();
    }

    public int getVulnCount() {
        return vulnCache.size();
    }

    public int getVulnPathCount() {
        return vulnCache.getPathCount();
    }
}
