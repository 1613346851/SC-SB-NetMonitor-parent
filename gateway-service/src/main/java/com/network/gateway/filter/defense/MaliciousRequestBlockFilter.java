package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 恶意请求拦截过滤器
 * 检测并拦截已知的恶意请求模式
 * 使用动态配置获取恶意UA列表、URI模式和开关
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class MaliciousRequestBlockFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(MaliciousRequestBlockFilter.class);

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    @Autowired
    private GatewayConfigCache configCache;

    private final Set<String> maliciousIps = ConcurrentHashMap.newKeySet();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());
        String userAgent = ServerWebExchangeUtil.extractUserAgent(
                ServerWebExchangeUtil.extractHeaders(exchange.getRequest()));
        String requestUri = exchange.getRequest().getURI().getPath().toLowerCase();

        try {
            if (shouldSkipMaliciousCheck(exchange)) {
                return chain.filter(exchange);
            }

            if (!isMaliciousRequestCheckEnabled()) {
                return chain.filter(exchange);
            }

            String blockReason = checkMaliciousRequest(sourceIp, userAgent, requestUri);
            
            if (blockReason != null) {
                return handleMaliciousRequest(exchange, sourceIp, userAgent, blockReason, startTime);
            }

            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("恶意请求检测过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    private boolean shouldSkipMaliciousCheck(ServerWebExchange exchange) {
        if (ServerWebExchangeUtil.isStaticResource(exchange)) {
            return true;
        }
        
        if (ServerWebExchangeUtil.isHealthCheck(exchange) || 
            ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
            return true;
        }
        
        return false;
    }

    private boolean isMaliciousRequestCheckEnabled() {
        return configCache.isDefenseEnabled("malicious-request");
    }

    private List<String> getMaliciousUserAgents() {
        return configCache.getMaliciousUserAgents();
    }

    private List<String> getMaliciousUriPatterns() {
        return configCache.getMaliciousUriPatterns();
    }

    private String checkMaliciousRequest(String sourceIp, String userAgent, String requestUri) {
        if (maliciousIps.contains(sourceIp)) {
            return "已知恶意IP地址";
        }

        if (userAgent != null) {
            String lowerUserAgent = userAgent.toLowerCase();
            List<String> maliciousAgents = getMaliciousUserAgents();
            for (String maliciousAgent : maliciousAgents) {
                if (lowerUserAgent.contains(maliciousAgent.toLowerCase())) {
                    return "可疑的扫描工具User-Agent: " + maliciousAgent;
                }
            }
        }

        List<String> maliciousPatterns = getMaliciousUriPatterns();
        for (String maliciousPattern : maliciousPatterns) {
            if (requestUri.contains(maliciousPattern.toLowerCase())) {
                return "访问敏感管理路径: " + maliciousPattern;
            }
        }

        if (checkAttackPatterns(requestUri, userAgent)) {
            return "检测到攻击模式";
        }

        return null;
    }

    private boolean checkAttackPatterns(String requestUri, String userAgent) {
        if (requestUri.contains("union") && requestUri.contains("select")) {
            return true;
        }

        if (requestUri.contains("'") && requestUri.contains("or") && requestUri.contains("=")) {
            return true;
        }

        if (requestUri.contains("<script") || requestUri.contains("javascript:")) {
            return true;
        }

        if (requestUri.contains("onerror=") || requestUri.contains("onload=")) {
            return true;
        }

        if (requestUri.contains("exec(") || requestUri.contains("system(")) {
            return true;
        }

        if (requestUri.contains("../") || requestUri.contains("..\\") || 
            requestUri.contains("%2e%2e/") || requestUri.contains("%252e%252e/")) {
            return true;
        }

        return false;
    }

    private Mono<Void> handleMaliciousRequest(ServerWebExchange exchange, String sourceIp, 
                                            String userAgent, String blockReason, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.BLOCK,
                sourceIp,
                "MALICIOUS_REQUEST_EVENT_" + System.currentTimeMillis(),
                blockReason
        );
        
        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                userAgent
        );
        defenseResult.setRiskLevel(DefenseResultBO.RiskLevel.HIGH);

        try {
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            logger.warn("拦截恶意请求: IP[{}] 原因[{}] URI[{}] User-Agent[{}]", 
                       sourceIp, blockReason,
                       exchange.getRequest().getURI().getPath(),
                       userAgent);

            defenseResult.setSuccessResult(400, "Bad Request - Malicious Activity Detected");
            return DefenseResponseUtil.buildMaliciousRequestResponse(
                    response, sourceIp, defenseResult.getEventId(), "HIGH");

        } catch (Exception e) {
            logger.error("处理恶意请求拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            
            return DefenseResponseUtil.buildMaliciousRequestResponse(
                    response, sourceIp, defenseResult.getEventId(), "HIGH");
        } finally {
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
            logger.debug("恶意请求防御执行完成: {}", DefenseLogUtil.buildExecutionSummary(defenseResult));
        }
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.MALICIOUS_REQUEST_BLOCK_FILTER_ORDER;
    }

    public String getFilterName() {
        return "MaliciousRequestBlockFilter";
    }

    public void addMaliciousIp(String ip) {
        if (ip != null && !ip.isEmpty()) {
            maliciousIps.add(ip);
            logger.info("添加恶意IP到拦截列表: {}", ip);
        }
    }

    public boolean removeMaliciousIp(String ip) {
        boolean removed = maliciousIps.remove(ip);
        if (removed) {
            logger.info("从拦截列表移除恶意IP: {}", ip);
        }
        return removed;
    }

    public void addMaliciousUserAgent(String userAgent) {
        logger.info("恶意UA列表现在由动态配置管理，手动添加已弃用: {}", userAgent);
    }

    public void addMaliciousUriPattern(String uriPattern) {
        logger.info("恶意URI模式列表现在由动态配置管理，手动添加已弃用: {}", uriPattern);
    }

    public int getMaliciousIpCount() {
        return maliciousIps.size();
    }

    public int getMaliciousUserAgentCount() {
        return getMaliciousUserAgents().size();
    }

    public int getMaliciousUriPatternCount() {
        return getMaliciousUriPatterns().size();
    }

    public String getStatistics() {
        return String.format("恶意请求拦截过滤器 - 恶意IP:%d 动态UA:%d 动态URI模式:%d 开关:%s", 
                getMaliciousIpCount(), 
                getMaliciousUserAgentCount(), 
                getMaliciousUriPatternCount(),
                isMaliciousRequestCheckEnabled() ? "开启" : "关闭");
    }

    public void clearDynamicPatterns() {
        int ipCount = maliciousIps.size();
        maliciousIps.clear();
        logger.info("清空动态恶意IP模式: {}", ipCount);
    }
}
