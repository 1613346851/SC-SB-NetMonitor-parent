package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 恶意请求拦截过滤器
 * 检测并拦截已知的恶意请求模式
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class MaliciousRequestBlockFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(MaliciousRequestBlockFilter.class);

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    /**
     * 已知的恶意用户代理集合
     */
    private final Set<String> maliciousUserAgents = ConcurrentHashMap.newKeySet();

    /**
     * 已知的恶意IP集合（可动态更新）
     */
    private final Set<String> maliciousIps = ConcurrentHashMap.newKeySet();

    /**
     * 恶意URI模式集合
     */
    private final Set<String> maliciousUriPatterns = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数 - 初始化已知的恶意模式
     */
    public MaliciousRequestBlockFilter() {
        initializeMaliciousPatterns();
    }

    /**
     * 初始化已知的恶意请求模式
     */
    private void initializeMaliciousPatterns() {
        // 恶意用户代理
        maliciousUserAgents.addAll(Set.of(
                "sqlmap", "nessus", "nmap", "burp suite", "zaproxy",
                "nikto", "w3af", "arachni", "skipfish", "wvs",
                "dirb", "gobuster", "ffuf", "hydra", "medusa"
        ));

        // 恶意URI模式
        maliciousUriPatterns.addAll(Set.of(
                "/admin", "/manager", "/console", "/wp-admin",
                "/phpmyadmin", "/mysql", "/dbadmin", "/webdav",
                "/.git/config", "/.env", "/config/database.yml",
                "/backup", "/dump", "/export", "/download"
        ));
    }

    /**
     * 过滤器核心方法
     *
     * @param exchange ServerWebExchange对象
     * @param chain 过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());
        String userAgent = ServerWebExchangeUtil.extractUserAgent(
                ServerWebExchangeUtil.extractHeaders(exchange.getRequest()));
        String requestUri = exchange.getRequest().getURI().getPath().toLowerCase();

        try {
            // 检查是否为恶意请求
            String blockReason = checkMaliciousRequest(sourceIp, userAgent, requestUri);
            
            if (blockReason != null) {
                return handleMaliciousRequest(exchange, sourceIp, userAgent, blockReason, startTime);
            }

            // 不是恶意请求，继续执行过滤器链
            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("恶意请求检测过程中发生异常，IP: {}", sourceIp, e);
            // 发生异常时继续执行，避免影响正常请求
            return chain.filter(exchange);
        }
    }

    /**
     * 检查是否为恶意请求
     *
     * @param sourceIp 源IP
     * @param userAgent 用户代理
     * @param requestUri 请求URI
     * @return 拦截原因，null表示不是恶意请求
     */
    private String checkMaliciousRequest(String sourceIp, String userAgent, String requestUri) {
        // 检查是否为已知恶意IP
        if (maliciousIps.contains(sourceIp)) {
            return "已知恶意IP地址";
        }

        // 检查用户代理是否可疑
        if (userAgent != null) {
            String lowerUserAgent = userAgent.toLowerCase();
            for (String maliciousAgent : maliciousUserAgents) {
                if (lowerUserAgent.contains(maliciousAgent)) {
                    return "可疑的扫描工具User-Agent: " + maliciousAgent;
                }
            }
        }

        // 检查URI是否匹配恶意模式
        for (String maliciousPattern : maliciousUriPatterns) {
            if (requestUri.contains(maliciousPattern)) {
                return "访问敏感管理路径: " + maliciousPattern;
            }
        }

        // 检查常见的攻击模式
        if (checkAttackPatterns(requestUri, userAgent)) {
            return "检测到攻击模式";
        }

        return null; // 不是恶意请求
    }

    /**
     * 检查攻击模式
     *
     * @param requestUri 请求URI
     * @param userAgent 用户代理
     * @return true表示检测到攻击模式
     */
    private boolean checkAttackPatterns(String requestUri, String userAgent) {
        // SQL注入检测
        if (requestUri.contains("union") && requestUri.contains("select")) {
            return true;
        }

        if (requestUri.contains("'") && requestUri.contains("or") && requestUri.contains("=")) {
            return true;
        }

        // XSS攻击检测
        if (requestUri.contains("<script") || requestUri.contains("javascript:")) {
            return true;
        }

        if (requestUri.contains("onerror=") || requestUri.contains("onload=")) {
            return true;
        }

        // 命令执行检测
        if (requestUri.contains("exec(") || requestUri.contains("system(")) {
            return true;
        }

        // 文件包含检测
        if (requestUri.contains("../") || requestUri.contains("..\\") || 
            requestUri.contains("%2e%2e/") || requestUri.contains("%252e%252e/")) {
            return true;
        }

        return false;
    }

    /**
     * 处理恶意请求
     *
     * @param exchange ServerWebExchange对象
     * @param sourceIp 源IP
     * @param userAgent 用户代理
     * @param blockReason 拦截原因
     * @param startTime 开始时间
     * @return Mono<Void>
     */
    private Mono<Void> handleMaliciousRequest(ServerWebExchange exchange, String sourceIp, 
                                            String userAgent, String blockReason, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 构建防御结果
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
            // 记录防御日志
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            logger.warn("拦截恶意请求: IP[{}] 原因[{}] URI[{}] User-Agent[{}]", 
                       sourceIp, blockReason,
                       exchange.getRequest().getURI().getPath(),
                       userAgent);

            // 构建并返回拦截响应
            defenseResult.setSuccessResult(400, "Bad Request - Malicious Activity Detected");
            return DefenseResponseUtil.buildMaliciousRequestResponse(
                    response, sourceIp, defenseResult.getEventId(), "HIGH");

        } catch (Exception e) {
            logger.error("处理恶意请求拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            
            // 即使日志推送失败，也要返回拦截响应
            return DefenseResponseUtil.buildMaliciousRequestResponse(
                    response, sourceIp, defenseResult.getEventId(), "HIGH");
        } finally {
            // 记录处理耗时
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
            logger.debug("恶意请求防御执行完成: {}", DefenseLogUtil.buildExecutionSummary(defenseResult));
        }
    }

    /**
     * 获取过滤器优先级
     *
     * @return 优先级数值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.MALICIOUS_REQUEST_BLOCK_FILTER_ORDER;
    }

    /**
     * 获取过滤器名称
     *
     * @return 过滤器名称
     */
    public String getFilterName() {
        return "MaliciousRequestBlockFilter";
    }

    /**
     * 动态添加恶意IP
     *
     * @param ip IP地址
     */
    public void addMaliciousIp(String ip) {
        if (ip != null && !ip.isEmpty()) {
            maliciousIps.add(ip);
            logger.info("添加恶意IP到拦截列表: {}", ip);
        }
    }

    /**
     * 动态移除恶意IP
     *
     * @param ip IP地址
     * @return true表示移除成功
     */
    public boolean removeMaliciousIp(String ip) {
        boolean removed = maliciousIps.remove(ip);
        if (removed) {
            logger.info("从拦截列表移除恶意IP: {}", ip);
        }
        return removed;
    }

    /**
     * 动态添加恶意用户代理
     *
     * @param userAgent 用户代理
     */
    public void addMaliciousUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            maliciousUserAgents.add(userAgent.toLowerCase());
            logger.info("添加恶意User-Agent到拦截列表: {}", userAgent);
        }
    }

    /**
     * 动态添加恶意URI模式
     *
     * @param uriPattern URI模式
     */
    public void addMaliciousUriPattern(String uriPattern) {
        if (uriPattern != null && !uriPattern.isEmpty()) {
            maliciousUriPatterns.add(uriPattern.toLowerCase());
            logger.info("添加恶意URI模式到拦截列表: {}", uriPattern);
        }
    }

    /**
     * 获取当前拦截的恶意IP数量
     *
     * @return 恶意IP数量
     */
    public int getMaliciousIpCount() {
        return maliciousIps.size();
    }

    /**
     * 获取当前拦截的恶意用户代理数量
     *
     * @return 恶意用户代理数量
     */
    public int getMaliciousUserAgentCount() {
        return maliciousUserAgents.size();
    }

    /**
     * 获取当前拦截的恶意URI模式数量
     *
     * @return 恶意URI模式数量
     */
    public int getMaliciousUriPatternCount() {
        return maliciousUriPatterns.size();
    }

    /**
     * 获取过滤器统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("恶意请求拦截过滤器 - 恶意IP:%d User-Agent:%d URI模式:%d", 
                           getMaliciousIpCount(), 
                           getMaliciousUserAgentCount(), 
                           getMaliciousUriPatternCount());
    }

    /**
     * 清空所有动态添加的恶意模式
     */
    public void clearDynamicPatterns() {
        int ipCount = maliciousIps.size();
        int uaCount = maliciousUserAgents.size() - 10; // 减去初始的10个
        int uriCount = maliciousUriPatterns.size() - 14; // 减去初始的14个
        
        maliciousIps.clear();
        maliciousUserAgents.retainAll(Set.of(
                "sqlmap", "nessus", "nmap", "burp suite", "zaproxy",
                "nikto", "w3af", "arachni", "skipfish", "wvs"
        ));
        maliciousUriPatterns.retainAll(Set.of(
                "/admin", "/manager", "/console", "/wp-admin",
                "/phpmyadmin", "/mysql", "/dbadmin", "/webdav",
                "/.git/config", "/.env", "/config/database.yml",
                "/backup", "/dump", "/export", "/download"
        ));
        
        logger.info("清空动态恶意模式: IP:{} User-Agent:{} URI模式:{}", 
                   ipCount, uaCount, uriCount);
    }
}