package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.RuleCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.dto.AttackRuleDTO;
import com.network.gateway.dto.DefenseLogDTO;
import com.network.gateway.util.DefenseLogUtil;
import com.network.gateway.util.DefenseResponseUtil;
import com.network.gateway.util.ServerWebExchangeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 攻击规则检测过滤器
 * 实现两阶段检测：
 * 1. 第一阶段：检测URL、Query参数、请求头、Cookie（无需读取请求体）
 * 2. 第二阶段：如果第一阶段未匹配，读取请求体进行检测
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class AttackRuleFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AttackRuleFilter.class);

    private static final List<String> BODY_METHODS = List.of("POST", "PUT", "PATCH");

    private static final List<String> TEXT_CONTENT_TYPES = List.of(
            "text/", "application/json", "application/xml", "application/x-www-form-urlencoded"
    );

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

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

            if (ruleCache.size() == 0) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String sourceIp = ServerWebExchangeUtil.extractSourceIp(request);

            RuleCache.MatchResult phase1Result = detectPhase1(exchange);
            if (phase1Result != null) {
                return handleAttackDetected(exchange, sourceIp, phase1Result, startTime, "Phase1");
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
        RuleCache.MatchResult uriResult = ruleCache.matchAll(uri);
        if (uriResult != null) {
            logger.debug("URL路径匹配规则: uri={}, rule={}", uri, uriResult.getRuleName());
            return uriResult;
        }

        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            RuleCache.MatchResult queryResult = ruleCache.matchAll(queryString);
            if (queryResult != null) {
                logger.debug("Query参数匹配规则: query={}, rule={}", queryString, queryResult.getRuleName());
                return queryResult;
            }
        }

        Map<String, String> headers = ServerWebExchangeUtil.extractHeaders(request);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerValue = entry.getValue();
            if (headerValue != null && !headerValue.isEmpty()) {
                RuleCache.MatchResult headerResult = ruleCache.matchAll(headerValue);
                if (headerResult != null) {
                    logger.debug("请求头匹配规则: header={}, rule={}", entry.getKey(), headerResult.getRuleName());
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
                .flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        String bodyContent = new String(bytes, StandardCharsets.UTF_8);

                        RuleCache.MatchResult bodyResult = ruleCache.matchAll(bodyContent);
                        if (bodyResult != null) {
                            logger.debug("请求体匹配规则: rule={}", bodyResult.getRuleName());
                            return handleAttackDetected(exchange, sourceIp, bodyResult, startTime, "Phase2");
                        }

                        ServerHttpRequest newRequest = request.mutate().build();
                        return chain.filter(exchange.mutate().request(newRequest).build());

                    } catch (Exception e) {
                        logger.error("请求体检测异常: {}", e.getMessage());
                        return chain.filter(exchange);
                    }
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> handleAttackDetected(ServerWebExchange exchange, String sourceIp,
                                            RuleCache.MatchResult matchResult, long startTime, String phase) {
        ServerHttpResponse response = exchange.getResponse();

        AttackRuleDTO rule = matchResult.getMatchedRule();
        String attackType = matchResult.getAttackType();
        String riskLevel = matchResult.getRiskLevel();

        String eventId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        DefenseLogDTO logDTO = DefenseLogUtil.buildBlockLog(
                sourceIp,
                eventId,
                DefenseResultBO.RiskLevel.valueOf(riskLevel),
                String.format("攻击规则匹配: %s [%s]", rule.getRuleName(), attackType)
        );
        logDTO.setRequestUri(exchange.getRequest().getURI().getPath());
        logDTO.setHttpMethod(exchange.getRequest().getMethodValue());
        logDTO.setDefenseReason(String.format("规则匹配[%s]: %s", rule.getAttackType(), rule.getRuleName()));
        logDTO.setAttackType(attackType);
        logDTO.setRiskLevel(riskLevel);

        try {
            defenseClient.pushDefenseLog(logDTO);

            logger.info("攻击检测拦截: ip={}, type={}, rule={}, phase={}, uri={}, eventId={}",
                    sourceIp, attackType, rule.getRuleName(), phase, 
                    exchange.getRequest().getURI().getPath(), eventId);

        } catch (Exception e) {
            logger.error("推送攻击检测日志失败: {}", e.getMessage());
        }

        return DefenseResponseUtil.buildMaliciousRequestResponse(response, sourceIp, eventId, riskLevel);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.ATTACK_RULE_FILTER_ORDER;
    }

    public String getFilterName() {
        return "AttackRuleFilter";
    }

    public String getStatistics() {
        return String.format("攻击规则检测过滤器统计:\n  - %s", ruleCache.getStats());
    }

    public int getRuleCount() {
        return ruleCache.size();
    }

    public int getTypeCount() {
        return ruleCache.getTypeCount();
    }
}
