package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimitFilter.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private RequestRateLimitCache rateLimitCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

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

            int currentState = attackStateCache.getState(sourceIp);
            
            if (currentState == IpAttackStateConstant.DEFENDED) {
                logger.debug("IP已处于DEFENDED状态，拦截请求: ip={}", sourceIp);
                return handleDefendedRequest(exchange, sourceIp, startTime, currentState);
            }

            if (currentState == IpAttackStateConstant.COOLDOWN) {
                if (attackStateCache.isInCooldown(sourceIp)) {
                    logger.debug("IP处于COOLDOWN状态，放行请求: ip={}", sourceIp);
                    return chain.filter(exchange);
                }
            }

            int threshold = getCurrentThreshold(sourceIp);
            boolean isRateLimited = isRateLimited(sourceIp, threshold);
            
            int currentRateLimitCount = attackStateCache.getRateLimitCount(sourceIp);
            
            if (isRateLimited) {
                int rateLimitTriggerWindowSeconds = configCache.getDdosRateLimitTriggerWindowSeconds();
                long windowMs = rateLimitTriggerWindowSeconds * 1000L;
                currentRateLimitCount = attackStateCache.incrementRateLimitCount(sourceIp, windowMs);
                
                int suspiciousThreshold = IpAttackStateConstant.SUSPICIOUS_RATE_LIMIT_THRESHOLD;
                int attackingThreshold = configCache.getDdosRateLimitTriggerCount();
                
                IpAttackStateCache.StateTransitionResult transitionResult = 
                    attackStateCache.checkAndTransitionState(sourceIp, currentRateLimitCount, suspiciousThreshold, attackingThreshold);
                
                if (transitionResult.isTransitioned()) {
                    handleStateTransition(sourceIp, transitionResult, exchange, startTime);
                    currentState = transitionResult.getNewState();
                }
                
                return handleRateLimitExceeded(exchange, sourceIp, startTime, threshold, currentRateLimitCount, currentState);
            }

            return chain.filter(exchange);
        } catch (Exception e) {
            logger.error("请求限流检查过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    private void handleStateTransition(String sourceIp, 
                                       IpAttackStateCache.StateTransitionResult transitionResult,
                                       ServerWebExchange exchange,
                                       long startTime) {
        int newState = transitionResult.getNewState();
        int previousState = transitionResult.getPreviousState();
        String reason = transitionResult.getReason();
        int stateRequestCount = transitionResult.getStateRequestCount();
        
        logger.info("IP状态转换完成: ip={}, {} -> {}, reason={}, stateRequestCount={}", 
                sourceIp,
                IpAttackStateConstant.getStateNameZh(previousState),
                IpAttackStateConstant.getStateNameZh(newState),
                reason,
                stateRequestCount);
        
        if (newState == IpAttackStateConstant.ATTACKING) {
            pushDDoSEvent(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, "攻击确认");
        } else if (newState == IpAttackStateConstant.DEFENDED) {
            pushDDoSEvent(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, "执行防御");
            pushBlacklistEvent(sourceIp, exchange, "攻击确认，自动拉黑");
        }
    }

    private Mono<Void> handleDefendedRequest(ServerWebExchange exchange, String sourceIp, long startTime, int currentState) {
        ServerHttpResponse response = exchange.getResponse();
        
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.BLACKLIST,
                sourceIp,
                attackStateCache.getEventId(sourceIp),
                "IP已被防御系统拦截"
        );

        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(DefenseResultBO.RiskLevel.HIGH);

        try {
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            pushTrafficData(exchange, sourceIp, currentState, true, 403, startTime);

            logger.debug("拦截DEFENDED状态请求: IP[{}] URI[{}]", 
                    sourceIp, exchange.getRequest().getURI().getPath());

            defenseResult.setSuccessResult(403, "Forbidden - IP Defended");
            return DefenseResponseUtil.buildForbiddenResponse(response, sourceIp, "IP已被防御系统拦截");
        } catch (Exception e) {
            logger.error("处理DEFENDED请求时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            return DefenseResponseUtil.buildForbiddenResponse(response, sourceIp, "IP已被防御系统拦截");
        } finally {
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
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
                                               int threshold,
                                               int rateLimitCount,
                                               int currentState) {
        ServerHttpResponse response = exchange.getResponse();
        int currentCount = rateLimitCache.getCurrentRequestCount(sourceIp);

        String eventId = attackStateCache.getEventId(sourceIp);

        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.RATE_LIMIT,
                sourceIp,
                eventId,
                String.format("请求频率过高(%d次/秒 > %d次/秒), 状态: %s", 
                        currentCount, threshold, IpAttackStateConstant.getStateNameZh(currentState))
        );

        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(currentCount > threshold * 2 ?
                DefenseResultBO.RiskLevel.HIGH : DefenseResultBO.RiskLevel.MEDIUM);

        try {
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            pushTrafficData(exchange, sourceIp, currentState, true, 429, startTime);

            logger.warn("限流拦截请求: IP[{}] 状态[{}] 当前频率{}次/秒 阈值{}次/秒 URI[{}] 方法[{}]",
                    sourceIp, IpAttackStateConstant.getStateNameZh(currentState),
                    currentCount, threshold,
                    exchange.getRequest().getURI().getPath(),
                    exchange.getRequest().getMethodValue());

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
    
    private void pushTrafficData(ServerWebExchange exchange, String sourceIp, int state, boolean defended, int responseStatus, long startTime) {
        try {
            TrafficMonitorDTO trafficDTO = new TrafficMonitorDTO();
            trafficDTO.setSourceIp(sourceIp);
            trafficDTO.setTargetIp(ServerWebExchangeUtil.extractTargetIp(exchange.getRequest()));
            trafficDTO.setRequestUri(exchange.getRequest().getURI().getPath());
            trafficDTO.setHttpMethod(exchange.getRequest().getMethodValue());
            trafficDTO.setUserAgent(ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest())));
            trafficDTO.setContentType(exchange.getRequest().getHeaders().getContentType() != null ? 
                exchange.getRequest().getHeaders().getContentType().toString() : null);
            trafficDTO.setStateTag(IpAttackStateConstant.getStateName(state));
            trafficDTO.setSuccess(!defended);
            trafficDTO.setResponseStatus(responseStatus);
            trafficDTO.setProcessingTime(System.currentTimeMillis() - startTime);
            trafficDTO.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            trafficDTO.setRequestTime(LocalDateTime.now().format(DATE_TIME_FORMATTER));
            
            trafficClient.pushTraffic(trafficDTO);
            
            logger.debug("推送拦截流量数据: ip={}, state={}, status={}", sourceIp, IpAttackStateConstant.getStateName(state), responseStatus);
        } catch (Exception e) {
            logger.error("推送拦截流量数据失败: ip={}, error={}", sourceIp, e.getMessage());
        }
    }

    private void pushDDoSEvent(String sourceIp, int rateLimitCount, ServerWebExchange exchange, String reason) {
        try {
            defenseClient.pushDDoSAttackEvent(
                sourceIp,
                rateLimitCount,
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
            );
            logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}, reason={}", sourceIp, rateLimitCount, reason);
        } catch (Exception e) {
            logger.error("推送DDoS攻击事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }

    private void pushBlacklistEvent(String sourceIp, ServerWebExchange exchange, String reason) {
        try {
            defenseClient.pushBlacklistEvent(
                sourceIp,
                "SYSTEM",
                reason,
                null
            );
            logger.info("黑名单事件推送成功: ip={}, reason={}", sourceIp, reason);
        } catch (Exception e) {
            logger.error("推送黑名单事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
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
