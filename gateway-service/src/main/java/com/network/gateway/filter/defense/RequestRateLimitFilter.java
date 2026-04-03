package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.IpAttackStateEntry;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.BlacklistEventDTO;
import com.network.gateway.dto.DDoSAttackEventDTO;
import com.network.gateway.dto.DefenseLogDTO;
import com.network.gateway.traffic.TrafficQueueManager;
import com.network.gateway.traffic.IpTrafficQueue;
import com.network.gateway.traffic.TrafficAggregateService;
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
    
    @Autowired
    private TrafficQueueManager trafficQueueManager;

    @Autowired
    private TrafficAggregateService aggregateService;

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
            
            logger.debug("限流检查: ip={}, threshold={}, isRateLimited={}, currentRateLimitCount={}", 
                    sourceIp, threshold, isRateLimited, currentRateLimitCount);
            
            if (isRateLimited) {
                int rateLimitTriggerWindowSeconds = configCache.getDdosRateLimitTriggerWindowSeconds();
                long windowMs = rateLimitTriggerWindowSeconds * 1000L;
                currentRateLimitCount = attackStateCache.incrementRateLimitCount(sourceIp, windowMs);
                
                int suspiciousThreshold = IpAttackStateConstant.SUSPICIOUS_RATE_LIMIT_THRESHOLD;
                int attackingThreshold = configCache.getDdosRateLimitTriggerCount();
                
                logger.info("限流触发: ip={}, currentRateLimitCount={}, suspiciousThreshold={}, attackingThreshold={}", 
                        sourceIp, currentRateLimitCount, suspiciousThreshold, attackingThreshold);
                
                IpAttackStateCache.StateTransitionResult transitionResult = 
                    attackStateCache.checkAndTransitionState(sourceIp, currentRateLimitCount, suspiciousThreshold, attackingThreshold);
                
                if (transitionResult.isTransitioned()) {
                    handleStateTransition(sourceIp, transitionResult, exchange, startTime);
                    currentState = transitionResult.getNewState();
                    
                    if (currentState == IpAttackStateConstant.DEFENDED) {
                        return handleDefendedRequest(exchange, sourceIp, startTime, currentState);
                    }
                } else {
                    pushAttackMonitorRecord(sourceIp, currentRateLimitCount, exchange, currentState, transitionResult.getEventId());
                }
                
                return handleRateLimitExceeded(exchange, sourceIp, startTime, threshold, currentRateLimitCount, currentState, transitionResult);
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
        int confidence = attackStateCache.getConfidence(sourceIp);
        String eventId = transitionResult.getEventId();
        
        logger.info("IP状态转换完成: ip={}, {} -> {}, reason={}, stateRequestCount={}, confidence={}, eventId={}", 
                sourceIp,
                IpAttackStateConstant.getStateNameZh(previousState),
                IpAttackStateConstant.getStateNameZh(newState),
                reason,
                stateRequestCount,
                confidence,
                eventId);
        
        aggregateService.onStateTransition(sourceIp, previousState, newState);
        
        DefenseResultBO.RiskLevel riskLevel = calculateRiskLevelByConfidence(confidence);
        
        if (newState == IpAttackStateConstant.SUSPICIOUS) {
            pushDDoSEvent(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, "频率异常", confidence, eventId);
            pushStateTransitionDefenseLog(sourceIp, exchange, eventId, "SUSPICIOUS", "频率异常检测", riskLevel, confidence);
        } else if (newState == IpAttackStateConstant.ATTACKING) {
            pushDDoSEvent(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, "攻击确认", confidence, eventId);
            pushStateTransitionDefenseLog(sourceIp, exchange, eventId, "ATTACKING", "攻击行为确认", riskLevel, confidence);
        } else if (newState == IpAttackStateConstant.DEFENDED) {
            pushDDoSEvent(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, "执行防御", confidence, eventId);
            pushBlacklistEvent(sourceIp, exchange, "攻击确认，自动拉黑", confidence, eventId);
        }
    }
    
    private void pushStateTransitionDefenseLog(String sourceIp, ServerWebExchange exchange, String eventId, 
                                                String stateName, String reason, 
                                                DefenseResultBO.RiskLevel riskLevel, int confidence) {
        try {
            DefenseResultBO defenseResult = new DefenseResultBO(
                    DefenseResultBO.DefenseType.RATE_LIMIT,
                    sourceIp,
                    eventId,
                    String.format("状态转换: %s, 原因: %s, 置信度: %d%%", stateName, reason, confidence)
            );
            
            defenseResult.setRequestInfo(
                    exchange.getRequest().getMethodValue(),
                    exchange.getRequest().getURI().getPath(),
                    ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
            );
            defenseResult.setRiskLevel(riskLevel);
            defenseResult.setAttackType("DDOS");
            defenseResult.setSuccessResult(429, "Too Many Requests - " + stateName);
            
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            logger.info("状态转换防御日志推送成功: ip={}, state={}, confidence={}, riskLevel={}, eventId={}", 
                    sourceIp, stateName, confidence, riskLevel, eventId);
        } catch (Exception e) {
            logger.error("推送状态转换防御日志失败: ip={}, state={}, error={}", sourceIp, stateName, e.getMessage(), e);
        }
    }

    private Mono<Void> handleDefendedRequest(ServerWebExchange exchange, String sourceIp, long startTime, int currentState) {
        ServerHttpResponse response = exchange.getResponse();
        
        boolean skipDefenseLog = attackStateCache.shouldSkipDefenseAction(sourceIp);
        String existingEventId = attackStateCache.getEventId(sourceIp);
        int confidence = attackStateCache.getConfidence(sourceIp);
        DefenseResultBO.RiskLevel riskLevel = calculateRiskLevelByConfidence(confidence);
        
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.BLACKLIST,
                sourceIp,
                existingEventId,
                String.format("IP已被防御系统拦截, 置信度: %d%%", confidence)
        );

        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(riskLevel);
        defenseResult.setAttackType("DDOS");

        try {
            defenseResult.setSuccessResult(403, "Forbidden - IP Defended");
            
            if (!skipDefenseLog) {
                DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
                defenseClient.pushDefenseLog(defenseLog);
            }

            logger.debug("拦截DEFENDED状态请求: IP[{}] URI[{}] skipLog={}", 
                    sourceIp, exchange.getRequest().getURI().getPath(), skipDefenseLog);

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
                                               int currentState,
                                               IpAttackStateCache.StateTransitionResult transitionResult) {
        ServerHttpResponse response = exchange.getResponse();
        int currentCount = rateLimitCache.getCurrentRequestCount(sourceIp);
        if (currentCount <= 0) {
            currentCount = threshold + 1;
        }

        String eventId = attackStateCache.getEventId(sourceIp);
        if ((eventId == null || eventId.isEmpty()) && transitionResult != null) {
            eventId = transitionResult.getEventId();
        }
        
        int confidence = attackStateCache.getConfidence(sourceIp);

        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.RATE_LIMIT,
                sourceIp,
                eventId,
                String.format("请求频率过高(%d次/秒 > %d次/秒), 状态: %s, 置信度: %d%%", 
                        currentCount, threshold, IpAttackStateConstant.getStateNameZh(currentState), confidence)
        );

        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(calculateRiskLevelByConfidence(confidence));
        defenseResult.setAttackType("DDOS");

        try {
            defenseResult.setSuccessResult(429, "Too Many Requests");
            
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);

            logger.warn("限流拦截请求: IP[{}] 状态[{}] 当前频率{}次/秒 阈值{}次/秒 URI[{}] 方法[{}] eventId={}",
                    sourceIp, IpAttackStateConstant.getStateNameZh(currentState),
                    currentCount, threshold,
                    exchange.getRequest().getURI().getPath(),
                    exchange.getRequest().getMethodValue(),
                    eventId);

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

    private void pushDDoSEvent(String sourceIp, int rateLimitCount, ServerWebExchange exchange, String reason, int confidence, String eventId) {
        try {
            int effectiveConfidence = confidence;
            if (effectiveConfidence <= 0 && rateLimitCount > 0) {
                effectiveConfidence = Math.min(30 + rateLimitCount * 5, 65);
            }
            if (effectiveConfidence <= 0) {
                effectiveConfidence = 30;
            }
            DDoSAttackEventDTO event = new DDoSAttackEventDTO(sourceIp, rateLimitCount, effectiveConfidence);
            event.setHttpMethod(exchange.getRequest().getMethodValue());
            event.setRequestUri(exchange.getRequest().getURI().getPath());
            event.setUserAgent(ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest())));
            if (eventId != null && !eventId.isEmpty()) {
                event.setEventId(eventId);
            }
            
            IpTrafficQueue queue = trafficQueueManager.getQueue(sourceIp);
            if (queue != null) {
                event.setSlidingWindowRps((int) queue.getSlidingWindowRps());
                event.setPeakRps((int) queue.getPeakSlidingRps());
            }
            
            IpAttackStateEntry entry = attackStateCache.get(sourceIp);
            if (entry != null) {
                event.setAttackDuration(entry.getAttackDuration());
                event.setRequestCount(entry.getAttackRequestCount());
                event.setUniqueUriCount(entry.getUniqueUriCount());
            }
            
            defenseClient.pushDDoSAttackEvent(event);
            logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}, confidence={} (original={}), reason={}, eventId={}, slidingRps={}, peakRps={}, requestCount={}", 
                    sourceIp, rateLimitCount, effectiveConfidence, confidence, reason, eventId, event.getSlidingWindowRps(), event.getPeakRps(), event.getRequestCount());
        } catch (Exception e) {
            logger.error("推送DDoS攻击事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }

    private void pushBlacklistEvent(String sourceIp, ServerWebExchange exchange, String reason, int confidence, String eventId) {
        try {
            long banDurationMs = calculateBanDuration(sourceIp, confidence);
            
            BlacklistEventDTO event = new BlacklistEventDTO(sourceIp, "SYSTEM", reason);
            event.setDurationSeconds(banDurationMs / 1000);
            event.setConfidence(confidence);
            event.setFromState(IpAttackStateConstant.ATTACKING);
            event.setToState(IpAttackStateConstant.DEFENDED);
            if (eventId != null && !eventId.isEmpty()) {
                event.setEventId(eventId);
            }
            defenseClient.pushBlacklistEvent(event);
            logger.info("黑名单事件推送成功: ip={}, reason={}, duration={}s, confidence={}, eventId={}", sourceIp, reason, banDurationMs / 1000, confidence, eventId);
        } catch (Exception e) {
            logger.error("推送黑名单事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }
    
    private void pushAttackMonitorRecord(String sourceIp, int rateLimitCount, ServerWebExchange exchange, 
                                          int currentState, String eventId) {
        try {
            int confidence = attackStateCache.getConfidence(sourceIp);
            if (confidence <= 0 && rateLimitCount > 0) {
                confidence = Math.min(30 + rateLimitCount * 5, 65);
            }
            if (confidence <= 0) {
                confidence = 30;
            }
            DDoSAttackEventDTO event = new DDoSAttackEventDTO(sourceIp, rateLimitCount, confidence);
            event.setHttpMethod(exchange.getRequest().getMethodValue());
            event.setRequestUri(exchange.getRequest().getURI().getPath());
            event.setUserAgent(ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest())));
            if (eventId != null && !eventId.isEmpty()) {
                event.setEventId(eventId);
            }
            
            IpTrafficQueue queue = trafficQueueManager.getQueue(sourceIp);
            if (queue != null) {
                event.setSlidingWindowRps((int) queue.getSlidingWindowRps());
                event.setPeakRps((int) queue.getPeakSlidingRps());
            }
            
            IpAttackStateEntry entry = attackStateCache.get(sourceIp);
            if (entry != null) {
                event.setAttackDuration(entry.getAttackDuration());
                event.setRequestCount(entry.getAttackRequestCount());
                event.setUniqueUriCount(entry.getUniqueUriCount());
            }
            
            event.setDescription(String.format("连续触发限流%d次，置信度%d%%，状态: %s", 
                    rateLimitCount, confidence, IpAttackStateConstant.getStateNameZh(currentState)));
            defenseClient.pushDDoSAttackEvent(event);
            logger.info("攻击监测记录推送成功: ip={}, rateLimitCount={}, confidence={}, state={}, eventId={}, slidingRps={}, peakRps={}, requestCount={}", 
                    sourceIp, rateLimitCount, confidence, IpAttackStateConstant.getStateNameZh(currentState), eventId, event.getSlidingWindowRps(), event.getPeakRps(), event.getRequestCount());
        } catch (Exception e) {
            logger.error("推送攻击监测记录失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }

    private long calculateBanDuration(String sourceIp, int confidence) {
        long baseDurationMs = configCache.getBanDurationBaseMs();
        int multiplier = configCache.getBanDurationMultiplier();
        
        IpAttackStateEntry entry = attackStateCache.get(sourceIp);
        int attackHistoryCount = entry != null ? entry.getAttackHistoryCount() : 0;
        
        long duration = baseDurationMs;
        
        if (confidence >= 90) {
            duration = baseDurationMs * multiplier;
        } else if (confidence >= 80) {
            duration = baseDurationMs * 3;
        } else if (confidence >= 70) {
            duration = baseDurationMs * 2;
        }
        
        if (attackHistoryCount > 0) {
            duration = duration * (1 + Math.min(attackHistoryCount, 3));
        }
        
        long maxDurationMs = 24 * 60 * 60 * 1000L;
        duration = Math.min(duration, maxDurationMs);
        
        logger.debug("计算封禁时长: ip={}, confidence={}, attackHistory={}, duration={}ms", 
            sourceIp, confidence, attackHistoryCount, duration);
        
        return duration;
    }
    
    private DefenseResultBO.RiskLevel calculateRiskLevelByConfidence(int confidence) {
        if (confidence >= 90) {
            return DefenseResultBO.RiskLevel.CRITICAL;
        } else if (confidence >= 70) {
            return DefenseResultBO.RiskLevel.HIGH;
        } else if (confidence >= 50) {
            return DefenseResultBO.RiskLevel.MEDIUM;
        } else {
            return DefenseResultBO.RiskLevel.LOW;
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
