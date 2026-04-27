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
import com.network.gateway.service.DistributedAttackDetector;
import com.network.gateway.service.SlowAttackDetector;
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
    private TrafficAggregateService aggregateService;

    @Autowired
    private SlowAttackDetector slowAttackDetector;

    @Autowired
    private DistributedAttackDetector distributedAttackDetector;

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
                    int threshold = getCurrentThreshold(sourceIp);
                    boolean isRateLimited = isRateLimited(sourceIp, threshold);
                    
                    if (isRateLimited) {
                        int rateLimitTriggerWindowSeconds = configCache.getDdosRateLimitTriggerWindowSeconds();
                        long windowMs = rateLimitTriggerWindowSeconds * 1000L;
                        int currentRateLimitCount = attackStateCache.incrementRateLimitCount(sourceIp, windowMs);
                        
                        int reattackThreshold = configCache.getStateCooldownToAttackingThresholdRps();
                        logger.info("COOLDOWN期间限流触发: ip={}, currentRateLimitCount={}, reattackThreshold={}", 
                                sourceIp, currentRateLimitCount, reattackThreshold);
                        
                        if (currentRateLimitCount >= reattackThreshold) {
                            IpAttackStateCache.StateTransitionResult transitionResult = 
                                attackStateCache.checkAndTransitionState(sourceIp, currentRateLimitCount, 
                                    IpAttackStateConstant.SUSPICIOUS_RATE_LIMIT_THRESHOLD, 
                                    configCache.getDdosRateLimitTriggerCount());
                            
                            if (transitionResult.isTransitioned() && transitionResult.getNewState() == IpAttackStateConstant.ATTACKING) {
                                logger.warn("COOLDOWN期间再次攻击，转换状态: ip={}, newState=ATTACKING", sourceIp);
                                handleStateTransition(sourceIp, transitionResult, exchange, startTime, true);
                                return handleDefendedRequest(exchange, sourceIp, startTime, IpAttackStateConstant.ATTACKING);
                            }
                        }
                    }
                    
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
                    handleStateTransition(sourceIp, transitionResult, exchange, startTime, true);
                    currentState = transitionResult.getNewState();
                    
                    if (currentState == IpAttackStateConstant.DEFENDED) {
                        return handleDefendedRequest(exchange, sourceIp, startTime, currentState);
                    }
                } else {
                    pushAttackMonitorRecord(sourceIp, currentRateLimitCount, exchange, currentState, transitionResult.getEventId());
                }
                
                return handleRateLimitExceeded(exchange, sourceIp, startTime, threshold, currentRateLimitCount, currentState, transitionResult);
            }

            if (currentState == IpAttackStateConstant.NORMAL || currentState == IpAttackStateConstant.SUSPICIOUS) {
                SlowAttackDetector.SlowAttackResult slowAttackResult = checkSlowAttack(sourceIp);
                logger.info("慢速攻击检测结果: ip={}, isSlowAttack={}, reason={}, duration={}ms, totalRequests={}, averageRps={}", 
                        sourceIp, slowAttackResult.isSlowAttack(), slowAttackResult.getReason(), 
                        slowAttackResult.getDuration(), slowAttackResult.getTotalRequests(), slowAttackResult.getAverageRps());
                if (slowAttackResult.isSlowAttack()) {
                    logger.warn("检测到慢速攻击: ip={}, reason={}, averageRps={}", 
                            sourceIp, slowAttackResult.getReason(), slowAttackResult.getAverageRps());
                    
                    IpAttackStateCache.StateTransitionResult slowTransitionResult = 
                        handleSlowAttack(sourceIp, slowAttackResult, exchange, startTime);
                    
                    if (slowTransitionResult != null && slowTransitionResult.isTransitioned()) {
                        if (slowTransitionResult.getNewState() == IpAttackStateConstant.DEFENDED) {
                            return handleDefendedRequest(exchange, sourceIp, startTime, slowTransitionResult.getNewState());
                        }
                    }
                }
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
                                       long startTime,
                                       boolean isHighFrequencyAttack) {
        int newState = transitionResult.getNewState();
        int previousState = transitionResult.getPreviousState();
        String reason = transitionResult.getReason();
        int stateRequestCount = transitionResult.getStateRequestCount();
        int confidence = attackStateCache.getConfidence(sourceIp);
        String eventId = transitionResult.getEventId();
        
        DistributedAttackDetector.DistributedAttackResult distributedResult = null;
        if (newState == IpAttackStateConstant.SUSPICIOUS || newState == IpAttackStateConstant.ATTACKING) {
            distributedResult = checkAndRecordDistributedAttack(sourceIp);
            if (distributedResult.isDistributedAttack()) {
                confidence = Math.min(confidence + 15, 100);
                attackStateCache.updateConfidence(sourceIp, confidence);
                logger.warn("检测到分布式攻击: ip={}, networkSegment={}, relatedIpCount={}, confidence={}", 
                        sourceIp, distributedResult.getNetworkSegment(), 
                        distributedResult.getRelatedIpCount(), confidence);
            }
        }
        
        logger.info("IP状态转换完成: ip={}, {} -> {}, reason={}, stateRequestCount={}, confidence={}, eventId={}, distributed={}", 
                sourceIp,
                IpAttackStateConstant.getStateNameZh(previousState),
                IpAttackStateConstant.getStateNameZh(newState),
                reason,
                stateRequestCount,
                confidence,
                eventId,
                distributedResult != null && distributedResult.isDistributedAttack());
        
        aggregateService.onStateTransition(sourceIp, previousState, newState);
        
        DefenseResultBO.RiskLevel riskLevel = calculateRiskLevelByConfidence(confidence);
        
        StringBuilder attackTypeDesc = new StringBuilder();
        attackTypeDesc.append(isHighFrequencyAttack ? "高频DDoS攻击" : "低频DDoS攻击");
        if (distributedResult != null && distributedResult.isDistributedAttack()) {
            attackTypeDesc.append("(分布式)");
        }
        
        if (newState == IpAttackStateConstant.SUSPICIOUS) {
            String eventReason = isHighFrequencyAttack ? "频率异常" : "慢速攻击";
            pushDDoSEventWithDistributed(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, 
                    eventReason, confidence, eventId, distributedResult, attackTypeDesc.toString());
            pushStateTransitionDefenseLog(sourceIp, exchange, eventId, "SUSPICIOUS", eventReason + "检测", riskLevel, confidence);
        } else if (newState == IpAttackStateConstant.ATTACKING) {
            String eventReason = isHighFrequencyAttack ? "攻击确认" : "慢速攻击确认";
            pushDDoSEventWithDistributed(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, 
                    eventReason, confidence, eventId, distributedResult, attackTypeDesc.toString());
            pushStateTransitionDefenseLog(sourceIp, exchange, eventId, "ATTACKING", eventReason, riskLevel, confidence);
        } else if (newState == IpAttackStateConstant.DEFENDED) {
            String eventReason = isHighFrequencyAttack ? "攻击确认，自动拉黑" : "慢速攻击确认，自动拉黑";
            pushDDoSEventWithDistributed(sourceIp, attackStateCache.getRateLimitCount(sourceIp), exchange, 
                    "执行防御", confidence, eventId, distributedResult, attackTypeDesc.toString());
            pushBlacklistEventWithDistributed(sourceIp, exchange, eventReason, confidence, eventId, distributedResult);
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
        
        IpAttackStateEntry entry = attackStateCache.get(sourceIp);
        if (entry != null) {
            int storedPeakRps = entry.getPeakRps();
            if (currentCount > storedPeakRps) {
                entry.setPeakRps(currentCount);
            }
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
            
            IpAttackStateEntry entry = attackStateCache.get(sourceIp);
            int peakRps = 0;
            if (entry != null) {
                peakRps = entry.getPeakRps();
                event.setAttackDuration(entry.getAttackDuration());
                event.setRequestCount(entry.getAttackRequestCount());
                event.setUniqueUriCount(entry.getUniqueUriCount());
            }
            
            event.setSlidingWindowRps(peakRps);
            event.setPeakRps(peakRps);
            
            defenseClient.pushDDoSAttackEvent(event);
            logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}, confidence={} (original={}), reason={}, eventId={}, peakRps={}, requestCount={}", 
                    sourceIp, rateLimitCount, effectiveConfidence, confidence, reason, eventId, peakRps, event.getRequestCount());
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
            
            IpAttackStateEntry entry = attackStateCache.get(sourceIp);
            int peakRps = 0;
            if (entry != null) {
                peakRps = entry.getPeakRps();
                event.setAttackDuration(entry.getAttackDuration());
                event.setRequestCount(entry.getAttackRequestCount());
                event.setUniqueUriCount(entry.getUniqueUriCount());
            }
            
            event.setSlidingWindowRps(peakRps);
            event.setPeakRps(peakRps);
            
            event.setDescription(String.format("连续触发限流%d次，置信度%d%%，状态: %s", 
                    rateLimitCount, confidence, IpAttackStateConstant.getStateNameZh(currentState)));
            defenseClient.pushDDoSAttackEvent(event);
            logger.info("攻击监测记录推送成功: ip={}, rateLimitCount={}, confidence={}, state={}, eventId={}, peakRps={}, requestCount={}", 
                    sourceIp, rateLimitCount, confidence, IpAttackStateConstant.getStateNameZh(currentState), eventId, peakRps, event.getRequestCount());
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

    private SlowAttackDetector.SlowAttackResult checkSlowAttack(String ip) {
        int currentCount = rateLimitCache.getCurrentRequestCount(ip);
        double currentRps = currentCount > 0 ? currentCount : 1.0;
        return slowAttackDetector.detectSlowAttack(ip, currentRps);
    }

    private IpAttackStateCache.StateTransitionResult handleSlowAttack(String sourceIp, 
                                                                        SlowAttackDetector.SlowAttackResult slowAttackResult,
                                                                        ServerWebExchange exchange,
                                                                        long startTime) {
        int currentState = attackStateCache.getState(sourceIp);
        
        if (currentState == IpAttackStateConstant.DEFENDED) {
            logger.debug("IP已处于DEFENDED状态，跳过慢速攻击处理: ip={}", sourceIp);
            return null;
        }
        
        int confidence = attackStateCache.getConfidence(sourceIp);
        confidence = Math.max(confidence, 50);
        confidence = Math.min(confidence + 15, 100);
        
        IpAttackStateCache.StateTransitionResult transitionResult;
        
        if (currentState == IpAttackStateConstant.NORMAL) {
            String eventId = generateEventId(sourceIp);
            attackStateCache.updateState(sourceIp, IpAttackStateConstant.SUSPICIOUS, eventId);
            attackStateCache.updateConfidence(sourceIp, confidence);
            
            transitionResult = new IpAttackStateCache.StateTransitionResult();
            transitionResult.setPreviousState(IpAttackStateConstant.NORMAL);
            transitionResult.setNewState(IpAttackStateConstant.SUSPICIOUS);
            transitionResult.setTransitioned(true);
            transitionResult.setReason("慢速攻击检测");
            transitionResult.setEventId(eventId);
            
            logger.warn("慢速攻击触发状态转换: ip={}, NORMAL -> SUSPICIOUS, confidence={}, reason={}", 
                    sourceIp, confidence, slowAttackResult.getReason());
        } else if (currentState == IpAttackStateConstant.SUSPICIOUS) {
            if (confidence >= 80) {
                String eventId = attackStateCache.getEventId(sourceIp);
                attackStateCache.updateState(sourceIp, IpAttackStateConstant.DEFENDED);
                attackStateCache.updateConfidence(sourceIp, confidence);
                
                transitionResult = new IpAttackStateCache.StateTransitionResult();
                transitionResult.setPreviousState(IpAttackStateConstant.SUSPICIOUS);
                transitionResult.setNewState(IpAttackStateConstant.DEFENDED);
                transitionResult.setTransitioned(true);
                transitionResult.setReason("慢速攻击确认，置信度达到防御阈值");
                transitionResult.setEventId(eventId);
                
                logger.warn("慢速攻击触发防御: ip={}, SUSPICIOUS -> DEFENDED, confidence={}", 
                        sourceIp, confidence);
            } else if (confidence >= 60) {
                String eventId = attackStateCache.getEventId(sourceIp);
                attackStateCache.updateState(sourceIp, IpAttackStateConstant.ATTACKING);
                attackStateCache.updateConfidence(sourceIp, confidence);
                
                transitionResult = new IpAttackStateCache.StateTransitionResult();
                transitionResult.setPreviousState(IpAttackStateConstant.SUSPICIOUS);
                transitionResult.setNewState(IpAttackStateConstant.ATTACKING);
                transitionResult.setTransitioned(true);
                transitionResult.setReason("慢速攻击持续");
                transitionResult.setEventId(eventId);
                
                logger.warn("慢速攻击持续触发状态转换: ip={}, SUSPICIOUS -> ATTACKING, confidence={}", 
                        sourceIp, confidence);
            } else {
                attackStateCache.updateConfidence(sourceIp, confidence);
                transitionResult = new IpAttackStateCache.StateTransitionResult();
                transitionResult.setPreviousState(currentState);
                transitionResult.setNewState(currentState);
                transitionResult.setTransitioned(false);
                transitionResult.setEventId(attackStateCache.getEventId(sourceIp));
            }
        } else if (currentState == IpAttackStateConstant.ATTACKING) {
            if (confidence >= 80) {
                String eventId = attackStateCache.getEventId(sourceIp);
                attackStateCache.updateState(sourceIp, IpAttackStateConstant.DEFENDED);
                attackStateCache.updateConfidence(sourceIp, confidence);
                
                transitionResult = new IpAttackStateCache.StateTransitionResult();
                transitionResult.setPreviousState(IpAttackStateConstant.ATTACKING);
                transitionResult.setNewState(IpAttackStateConstant.DEFENDED);
                transitionResult.setTransitioned(true);
                transitionResult.setReason("慢速攻击确认，置信度达到防御阈值");
                transitionResult.setEventId(eventId);
                
                logger.warn("慢速攻击触发防御: ip={}, ATTACKING -> DEFENDED, confidence={}", 
                        sourceIp, confidence);
            } else {
                confidence = Math.min(confidence + 5, 100);
                attackStateCache.updateConfidence(sourceIp, confidence);
                transitionResult = new IpAttackStateCache.StateTransitionResult();
                transitionResult.setPreviousState(currentState);
                transitionResult.setNewState(currentState);
                transitionResult.setTransitioned(false);
                transitionResult.setEventId(attackStateCache.getEventId(sourceIp));
            }
        } else {
            transitionResult = new IpAttackStateCache.StateTransitionResult();
            transitionResult.setPreviousState(currentState);
            transitionResult.setNewState(currentState);
            transitionResult.setTransitioned(false);
            transitionResult.setEventId(attackStateCache.getEventId(sourceIp));
        }
        
        if (transitionResult.isTransitioned()) {
            handleStateTransition(sourceIp, transitionResult, exchange, startTime, false);
        }
        
        return transitionResult;
    }

    private DistributedAttackDetector.DistributedAttackResult checkAndRecordDistributedAttack(String ip) {
        distributedAttackDetector.recordAttack(ip);
        return distributedAttackDetector.detectDistributedAttack(ip);
    }

    private void pushDDoSEventWithDistributed(String sourceIp, int rateLimitCount, ServerWebExchange exchange, 
                                               String reason, int confidence, String eventId,
                                               DistributedAttackDetector.DistributedAttackResult distributedResult,
                                               String attackTypeDesc) {
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
            
            IpAttackStateEntry entry = attackStateCache.get(sourceIp);
            int peakRps = 0;
            if (entry != null) {
                peakRps = entry.getPeakRps();
                event.setAttackDuration(entry.getAttackDuration());
                event.setRequestCount(entry.getAttackRequestCount());
                event.setUniqueUriCount(entry.getUniqueUriCount());
            }
            
            event.setSlidingWindowRps(peakRps);
            event.setPeakRps(peakRps);
            event.setAttackType("DDOS");
            
            String description = String.format("[%s] %s", attackTypeDesc, event.getDescription());
            if (distributedResult != null && distributedResult.isDistributedAttack()) {
                description += String.format(" [分布式攻击: 网段=%s, 关联IP数=%d]", 
                        distributedResult.getNetworkSegment(), distributedResult.getRelatedIpCount());
            }
            event.setDescription(description);
            
            defenseClient.pushDDoSAttackEvent(event);
            logger.info("DDoS攻击事件推送成功: ip={}, rateLimitCount={}, confidence={}, reason={}, eventId={}, attackTypeDesc={}, distributed={}", 
                    sourceIp, rateLimitCount, effectiveConfidence, reason, eventId, attackTypeDesc, 
                    distributedResult != null && distributedResult.isDistributedAttack());
        } catch (Exception e) {
            logger.error("推送DDoS攻击事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }

    private void pushBlacklistEventWithDistributed(String sourceIp, ServerWebExchange exchange, String reason, 
                                                    int confidence, String eventId,
                                                    DistributedAttackDetector.DistributedAttackResult distributedResult) {
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
            
            if (distributedResult != null && distributedResult.isDistributedAttack()) {
                event.setBanReason(String.format("%s [分布式攻击: 网段=%s, 关联IP数=%d]", 
                        reason, distributedResult.getNetworkSegment(), distributedResult.getRelatedIpCount()));
            }
            
            defenseClient.pushBlacklistEvent(event);
            logger.info("黑名单事件推送成功: ip={}, reason={}, duration={}s, confidence={}, eventId={}, distributed={}", 
                    sourceIp, reason, banDurationMs / 1000, confidence, eventId, 
                    distributedResult != null && distributedResult.isDistributedAttack());
        } catch (Exception e) {
            logger.error("推送黑名单事件失败: ip={}, error={}", sourceIp, e.getMessage(), e);
        }
    }

    private String generateEventId(String ip) {
        return "DDOS_" + System.currentTimeMillis() + "_" + ip.hashCode();
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
