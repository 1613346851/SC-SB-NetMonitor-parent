package com.network.gateway.filter.collect;

import com.network.gateway.bo.RawTrafficBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.TrafficTempCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.enums.TrafficPushStrategy;
import com.network.gateway.handler.TrafficPushStrategyManager;
import com.network.gateway.traffic.*;
import com.network.gateway.util.ServerWebExchangeUtil;
import com.network.gateway.util.TrafficPreProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TrafficCollectGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TrafficCollectGlobalFilter.class);

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private TrafficTempCache trafficTempCache;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private TrafficPushStrategyManager strategyManager;

    @Autowired
    private TrafficQueueManager queueManager;

    @Autowired
    private PushRetryQueue retryQueue;

    @Autowired
    private PushDegradationHandler degradationHandler;

    @Autowired
    private TrafficEventProcessor eventProcessor;

    @Autowired
    private TrafficActivityService activityService;

    @Autowired
    private TrafficAggregateService aggregateService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (shouldSkipCollection(exchange)) {
                logger.debug("跳过流量采集：{}", exchange.getRequest().getURI().getPath());
                return chain.filter(exchange);
            }

            RawTrafficBO rawTraffic = ServerWebExchangeUtil.extractTrafficInfo(exchange);
            String requestId = rawTraffic.getRequestId();
            String sourceIp = rawTraffic.getSourceIp();
            
            if (trafficTempCache.containsTraffic(requestId)) {
                logger.debug("请求已采集，跳过：{}", requestId);
                return chain.filter(exchange);
            }

            attackStateCache.incrementRequestCount(sourceIp);
            
            logger.debug("开始采集流量：请求[{}] {} {} 来自IP[{}]", 
                requestId, rawTraffic.getMethod(), rawTraffic.getUri(), sourceIp);

            return handleTraffic(exchange, chain, rawTraffic, sourceIp, startTime);

        } catch (Exception e) {
            logger.error("流量采集过程中发生异常", e);
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleTraffic(ServerWebExchange exchange, GatewayFilterChain chain,
                                     RawTrafficBO rawTraffic, String sourceIp, long startTime) {
        return chain.filter(exchange)
                .doOnTerminate(() -> {
                    try {
                        processTrafficAfterResponse(exchange, rawTraffic, sourceIp, startTime);
                    } catch (Exception e) {
                        logger.error("流量处理失败：ip={}", sourceIp, e);
                    }
                })
                .doOnError(throwable -> {
                    logger.warn("请求处理错误：ip={}, error={}", sourceIp, throwable.getMessage());
                });
    }

    private void processTrafficAfterResponse(ServerWebExchange exchange, RawTrafficBO rawTraffic, 
                                             String sourceIp, long startTime) {
        Boolean attackIntercepted = exchange.getAttribute("attack_intercepted");
        if (attackIntercepted != null && attackIntercepted) {
            logger.debug("请求已被拦截，跳过流量推送：ip={}, uri={}", sourceIp, rawTraffic.getUri());
            return;
        }
        
        long endTime = System.currentTimeMillis();
        int statusCode = exchange.getResponse().getStatusCode() != null ? 
                       exchange.getResponse().getStatusCode().value() : 200;
        
        int finalState = attackStateCache.getState(sourceIp);
        String finalEventId = attackStateCache.getEventId(sourceIp);
        int confidence = attackStateCache.getConfidence(sourceIp);
        
        TrafficPushStrategy finalStrategy = getPushStrategy(finalState);
        
        logger.debug("流量处理：ip={}, uri={}, state={}, status={}, strategy={}", 
            sourceIp, rawTraffic.getUri(), 
            IpAttackStateConstant.getStateNameZh(finalState), statusCode, finalStrategy);
        
        if (finalStrategy == TrafficPushStrategy.REALTIME) {
            doRealtimePush(rawTraffic, sourceIp, finalState, finalEventId, statusCode, endTime - startTime);
        } else if (finalStrategy == TrafficPushStrategy.AGGREGATE) {
            doAggregatePush(rawTraffic, sourceIp, finalState, finalEventId, confidence, statusCode, endTime - startTime);
        }
    }

    private void doRealtimePush(RawTrafficBO rawTraffic, String sourceIp, int state, 
                                String eventId, int statusCode, long processingTime) {
        TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);
        monitorDTO.setIsAggregated(false);
        monitorDTO.setResponseInfo(statusCode, "", processingTime);
        monitorDTO.setAvgProcessingTime(processingTime);
        monitorDTO.setStateTag(IpAttackStateConstant.getStateName(state));
        
        if (eventId != null && !eventId.isEmpty()) {
            monitorDTO.setEventId(eventId);
        }
        
        try {
            trafficClient.pushTraffic(monitorDTO);
            logger.debug("实时推送完成：ip={}, uri={}, state={}, status={}, 耗时{}ms", 
                sourceIp, rawTraffic.getUri(), 
                IpAttackStateConstant.getStateNameZh(state), statusCode, processingTime);
        } catch (Exception e) {
            logger.error("实时推送失败：ip={}, uri={}, error={}", 
                sourceIp, rawTraffic.getUri(), e.getMessage());
        }
    }

    private void doAggregatePush(RawTrafficBO rawTraffic, String sourceIp, int state, 
                                  String eventId, int confidence, int statusCode, long processingTime) {
        TrafficSample sample = new TrafficSample();
        sample.setRequestId(rawTraffic.getRequestId());
        sample.setRequestUri(rawTraffic.getUri());
        sample.setHttpMethod(rawTraffic.getMethod());
        sample.setHeaders(rawTraffic.getHeaders());
        sample.setRequestBody(rawTraffic.getRequestBody());
        sample.setTimestamp(System.currentTimeMillis());
        sample.setState(state);
        sample.setStateName(IpAttackStateConstant.getStateNameZh(state));
        sample.setConfidence(confidence);
        sample.setResponseStatus(statusCode);
        sample.setProcessingTime(processingTime);
        sample.setError(statusCode >= 400);
        sample.setTargetIp(rawTraffic.getTargetIp());
        sample.setTargetPort(rawTraffic.getTargetPort());
        sample.setProtocol(rawTraffic.getProtocol());
        sample.setUserAgent(rawTraffic.getUserAgent());
        sample.setSourcePort(rawTraffic.getSourcePort());
        
        if (eventId != null && !eventId.isEmpty()) {
            sample.setEventId(eventId);
        }
        
        aggregateService.addSample(sourceIp, sample);
        
        logger.debug("流量已加入聚合队列：ip={}, uri={}, state={}, status={}", 
            sourceIp, sample.getRequestUri(), 
            IpAttackStateConstant.getStateNameZh(state), statusCode);
    }

    private TrafficPushStrategy getPushStrategy(int state) {
        switch (state) {
            case IpAttackStateConstant.NORMAL:
                return TrafficPushStrategy.REALTIME;
            
            case IpAttackStateConstant.SUSPICIOUS:
            case IpAttackStateConstant.ATTACKING:
            case IpAttackStateConstant.DEFENDED:
            case IpAttackStateConstant.COOLDOWN:
                return TrafficPushStrategy.AGGREGATE;
            
            default:
                return TrafficPushStrategy.REALTIME;
        }
    }

    private boolean shouldSkipCollection(ServerWebExchange exchange) {
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

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.TRAFFIC_COLLECT_FILTER_ORDER;
    }

    public String getFilterName() {
        return "TrafficCollectGlobalFilter";
    }

    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("流量采集过滤器统计:\n");
        sb.append(String.format("  - 缓存流量数: %d\n", trafficTempCache.getSize()));
        sb.append(String.format("  - 缓存统计: %s\n", trafficTempCache.getStats()));
        sb.append(String.format("  - 聚合服务: %s\n", aggregateService.getStatistics()));
        sb.append(String.format("  - 事件处理器: %s\n", eventProcessor.getStatus()));
        return sb.toString();
    }
}
