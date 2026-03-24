package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
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

/**
 * 请求限流过滤器
 * 使用动态配置获取限流阈值和开关
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimitFilter.class);

    @Autowired
    private RequestRateLimitCache rateLimitCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());

        try {
            if (shouldSkipRateLimit(exchange)) {
                return chain.filter(exchange);
            }

            if (!isRateLimitEnabled()) {
                return chain.filter(exchange);
            }

            if (attackStateCache.isInDefendedState(sourceIp)) {
                logger.debug("IP已处于DEFENDED状态，跳过限流检查: ip={}", sourceIp);
                return chain.filter(exchange);
            }

            int threshold = getCurrentThreshold(sourceIp);
            if (isRateLimited(sourceIp, threshold)) {
                return handleRateLimitExceeded(exchange, sourceIp, startTime, threshold);
            }
            return chain.filter(exchange);
        } catch (Exception e) {
            logger.error("请求限流检查过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    private boolean shouldSkipRateLimit(ServerWebExchange exchange) {
        if (ServerWebExchangeUtil.isStaticResource(exchange)) {
            return true;
        }
        
        if (ServerWebExchangeUtil.isHealthCheck(exchange) || 
            ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
            return true;
        }
        
        return false;
    }

    private boolean isRateLimitEnabled() {
        return configCache.isDefenseEnabled("rate-limit");
    }

    private boolean isRateLimited(String ip, int threshold) {
        return rateLimitCache.checkAndIncrement(ip, threshold);
    }

    private int getCurrentThreshold(String ip) {
        int defaultThreshold = configCache.getRateLimitThreshold();
        return rateLimitCache.getEffectiveThreshold(ip, defaultThreshold);
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange,
                                               String sourceIp,
                                               long startTime,
                                               int threshold) {
        ServerHttpResponse response = exchange.getResponse();
        int currentCount = rateLimitCache.getCurrentRequestCount(sourceIp);

        attackStateCache.incrementRateLimitCount(sourceIp);
        int currentState = attackStateCache.getState(sourceIp);
        if (currentState == IpAttackStateConstant.NORMAL) {
            attackStateCache.markAsSuspicious(sourceIp);
            logger.info("IP状态更新为SUSPICIOUS: ip={}, reason=rate_limit_exceeded", sourceIp);
        }

        boolean skipDefenseLog = attackStateCache.shouldSkipDefenseAction(sourceIp);
        String eventId = attackStateCache.getEventId(sourceIp);

        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.RATE_LIMIT,
                sourceIp,
                eventId,
                String.format("请求频率过高(%d次/秒 > %d次/秒)", currentCount, threshold)
        );

        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(currentCount > threshold * 2 ?
                DefenseResultBO.RiskLevel.HIGH : DefenseResultBO.RiskLevel.MEDIUM);

        try {
            if (!skipDefenseLog) {
                DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
                defenseClient.pushDefenseLog(defenseLog);
            }

            logger.warn("限流拦截请求: IP[{}] 当前频率{}次/秒 阈值{}次/秒 URI[{}] 方法[{}] eventId[{}]",
                    sourceIp, currentCount, threshold,
                    exchange.getRequest().getURI().getPath(),
                    exchange.getRequest().getMethodValue(),
                    eventId);

            defenseResult.setSuccessResult(429, "Too Many Requests");
            return DefenseResponseUtil.buildRateLimitResponse(response, sourceIp, threshold);
        } catch (Exception e) {
            logger.error("处理限流拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            return DefenseResponseUtil.buildRateLimitResponse(response, sourceIp, threshold);
        } finally {
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
            logger.debug("限流防御执行完成: {}", DefenseLogUtil.buildExecutionSummary(defenseResult));
        }
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.REQUEST_RATE_LIMIT_FILTER_ORDER;
    }

    public String getFilterName() {
        return "RequestRateLimitFilter";
    }

    public int getActiveIpCount() {
        return rateLimitCache.getSize();
    }

    public int getCurrentRequestCount(String ip) {
        return rateLimitCache.getCurrentRequestCount(ip);
    }

    public int getDefaultThreshold() {
        return configCache.getRateLimitThreshold();
    }

    public int getCurrentThresholdForIp(String ip) {
        return getCurrentThreshold(ip);
    }

    public void resetRequestCount(String ip) {
        rateLimitCache.resetRequestCount(ip);
    }

    public void cleanupExpiredRecords() {
        rateLimitCache.cleanupExpired();
    }

    public java.util.Set<String> getHighFrequencyIps(double ratio) {
        int threshold = configCache.getRateLimitThreshold();
        return rateLimitCache.getHighFrequencyIps(threshold, ratio);
    }

    public String getStatistics() {
        int threshold = configCache.getRateLimitThreshold();
        RequestRateLimitCache.RateLimitStatistics stats = rateLimitCache.getStatistics(threshold);
        return String.format("请求限流过滤器 - 动态阈值:%d次/秒 开关:%s %s", 
                threshold, isRateLimitEnabled() ? "开启" : "关闭", stats.toString());
    }

    public int batchResetRequestCounts(java.util.Set<String> ips) {
        return rateLimitCache.batchResetRequestCount(ips);
    }
}
