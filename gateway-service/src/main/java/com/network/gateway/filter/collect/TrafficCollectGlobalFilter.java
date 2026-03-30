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

            int currentState = attackStateCache.getState(sourceIp);
            TrafficPushStrategy pushStrategy = getPushStrategy(currentState);

            attackStateCache.incrementRequestCount(sourceIp);
            
            logger.debug("开始采集流量：{} 状态={} 推送策略={}", 
                rawTraffic.getTrafficSummary(), 
                IpAttackStateConstant.getStateName(currentState),
                pushStrategy);

            return handleTraffic(exchange, chain, rawTraffic, sourceIp, currentState, pushStrategy, startTime);

        } catch (Exception e) {
            logger.error("流量采集过程中发生异常", e);
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleTraffic(ServerWebExchange exchange, GatewayFilterChain chain,
                                     RawTrafficBO rawTraffic, String sourceIp, 
                                     int currentState, TrafficPushStrategy pushStrategy, 
                                     long startTime) {
        
        if (pushStrategy == TrafficPushStrategy.REALTIME) {
            return handleRealtimePush(exchange, chain, rawTraffic, sourceIp, currentState, startTime);
        } else if (pushStrategy == TrafficPushStrategy.AGGREGATE) {
            return handleAggregatePush(exchange, chain, rawTraffic, sourceIp, currentState, startTime);
        } else if (pushStrategy == TrafficPushStrategy.SAMPLING) {
            return handleSamplingPush(exchange, chain, rawTraffic, sourceIp, currentState, startTime);
        } else {
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleRealtimePush(ServerWebExchange exchange, GatewayFilterChain chain,
                                          RawTrafficBO rawTraffic, String sourceIp, 
                                          int currentState, long startTime) {
        TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);
        monitorDTO.setIsAggregated(false);
        
        String eventId = attackStateCache.getEventId(sourceIp);
        if (eventId != null && !eventId.isEmpty()) {
            monitorDTO.setEventId(eventId);
        }
        
        return chain.filter(exchange)
                .doOnSuccess(unused -> {
                    try {
                        long endTime = System.currentTimeMillis();
                        int statusCode = exchange.getResponse().getStatusCode() != null ? 
                                       exchange.getResponse().getStatusCode().value() : 200;
                        
                        monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);
                        monitorDTO.setStateTag(IpAttackStateConstant.getStateName(currentState));
                        
                        doRealtimePush(monitorDTO);
                        
                        logger.debug("实时推送完成：ip={}, uri={}, 耗时{}ms", 
                            sourceIp, rawTraffic.getUri(), endTime - startTime);
                    } catch (Exception e) {
                        logger.error("实时推送失败：ip={}", sourceIp, e);
                    }
                })
                .doOnError(throwable -> {
                    logger.warn("请求处理错误：ip={}, error={}", sourceIp, throwable.getMessage());
                });
    }

    private Mono<Void> handleAggregatePush(ServerWebExchange exchange, GatewayFilterChain chain,
                                           RawTrafficBO rawTraffic, String sourceIp, 
                                           int currentState, long startTime) {
        TrafficSample sample = createTrafficSample(rawTraffic, currentState, sourceIp);
        
        return chain.filter(exchange)
                .doOnSuccess(unused -> {
                    try {
                        long endTime = System.currentTimeMillis();
                        int statusCode = exchange.getResponse().getStatusCode() != null ? 
                                       exchange.getResponse().getStatusCode().value() : 200;
                        
                        sample.setResponseStatus(statusCode);
                        sample.setProcessingTime(endTime - startTime);
                        sample.setError(statusCode >= 400);
                        
                        aggregateService.addSample(sourceIp, sample);
                        
                        logger.debug("流量已加入聚合队列：ip={}, uri={}, state={}", 
                            sourceIp, sample.getRequestUri(), 
                            IpAttackStateConstant.getStateNameZh(currentState));
                    } catch (Exception e) {
                        logger.error("聚合处理失败：ip={}", sourceIp, e);
                    }
                })
                .doOnError(throwable -> {
                    try {
                        sample.setError(true);
                        sample.setErrorMessage(throwable.getMessage());
                        aggregateService.addSample(sourceIp, sample);
                    } catch (Exception e) {
                        logger.error("聚合处理错误失败：ip={}", sourceIp, e);
                    }
                });
    }

    private Mono<Void> handleSamplingPush(ServerWebExchange exchange, GatewayFilterChain chain,
                                          RawTrafficBO rawTraffic, String sourceIp, 
                                          int currentState, long startTime) {
        int samplingRate = configCache.getTrafficPushSamplingRate();
        
        if (shouldSample(samplingRate)) {
            TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);
            monitorDTO.setIsAggregated(false);
            
            return chain.filter(exchange)
                    .doOnSuccess(unused -> {
                        try {
                            long endTime = System.currentTimeMillis();
                            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                                           exchange.getResponse().getStatusCode().value() : 200;
                            
                            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);
                            monitorDTO.setStateTag(IpAttackStateConstant.getStateName(currentState));
                            
                            doRealtimePush(monitorDTO);
                            
                            logger.debug("采样推送完成：ip={}, uri={}", sourceIp, rawTraffic.getUri());
                        } catch (Exception e) {
                            logger.error("采样推送失败：ip={}", sourceIp, e);
                        }
                    });
        } else {
            logger.debug("采样跳过：ip={}, rate=1/{}", sourceIp, samplingRate);
            return chain.filter(exchange);
        }
    }

    private boolean shouldSample(int samplingRate) {
        if (samplingRate <= 1) {
            return true;
        }
        return (System.currentTimeMillis() % samplingRate) == 0;
    }

    private TrafficSample createTrafficSample(RawTrafficBO rawTraffic, int currentState, String sourceIp) {
        TrafficSample sample = new TrafficSample();
        sample.setRequestId(rawTraffic.getRequestId());
        sample.setRequestUri(rawTraffic.getUri());
        sample.setHttpMethod(rawTraffic.getMethod());
        sample.setHeaders(rawTraffic.getHeaders());
        sample.setRequestBody(rawTraffic.getRequestBody());
        sample.setTimestamp(System.currentTimeMillis());
        sample.setState(currentState);
        sample.setStateName(IpAttackStateConstant.getStateNameZh(currentState));
        sample.setConfidence(attackStateCache.getConfidence(sourceIp));
        
        String eventId = attackStateCache.getEventId(sourceIp);
        if (eventId != null && !eventId.isEmpty()) {
            sample.setEventId(eventId);
        }
        
        return sample;
    }

    private TrafficPushStrategy getPushStrategy(int state) {
        switch (state) {
            case IpAttackStateConstant.NORMAL:
                return TrafficPushStrategy.REALTIME;
            
            case IpAttackStateConstant.SUSPICIOUS:
            case IpAttackStateConstant.ATTACKING:
            case IpAttackStateConstant.DEFENDED:
                return TrafficPushStrategy.AGGREGATE;
            
            case IpAttackStateConstant.COOLDOWN:
                return TrafficPushStrategy.SAMPLING;
            
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

    private void doRealtimePush(TrafficMonitorDTO monitorDTO) {
        try {
            trafficClient.pushTraffic(monitorDTO);
            logger.info("实时推送流量成功：ip={}, uri={}, isAggregated=false", 
                monitorDTO.getSourceIp(), monitorDTO.getRequestUri());
        } catch (Exception e) {
            logger.error("实时推送流量失败：ip={}, uri={}, error={}", 
                monitorDTO.getSourceIp(), monitorDTO.getRequestUri(), e.getMessage());
        }
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
