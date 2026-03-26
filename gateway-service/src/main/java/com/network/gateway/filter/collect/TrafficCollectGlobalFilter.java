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
import com.network.gateway.util.ServerWebExchangeUtil;
import com.network.gateway.util.TrafficPreProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
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
            TrafficPushStrategy pushStrategy = getPushStrategy(currentState, sourceIp);

            attackStateCache.incrementRequestCount(sourceIp);
            
            logger.debug("开始采集流量：{} 状态={} 推送策略={}", 
                rawTraffic.getTrafficSummary(), 
                IpAttackStateConstant.getStateName(currentState),
                pushStrategy);

            TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);
            monitorDTO.setSkipPush(pushStrategy == TrafficPushStrategy.SKIP);

            trafficTempCache.putTraffic(requestId, monitorDTO);

            final TrafficPushStrategy finalPushStrategy = pushStrategy;

            return chain.filter(exchange)
                    .doOnSuccess(unused -> {
                        handleRequestSuccess(exchange, monitorDTO, startTime, finalPushStrategy);
                    })
                    .doOnError(throwable -> {
                        handleRequestError(exchange, monitorDTO, throwable, startTime, finalPushStrategy);
                    });

        } catch (Exception e) {
            logger.error("流量采集过程中发生异常", e);
            return chain.filter(exchange);
        }
    }

    private TrafficPushStrategy getPushStrategy(int state, String sourceIp) {
        switch (state) {
            case IpAttackStateConstant.DEFENDED:
                return TrafficPushStrategy.COUNTER_ONLY;
            
            case IpAttackStateConstant.SUSPICIOUS:
            case IpAttackStateConstant.COOLDOWN:
                return TrafficPushStrategy.SAMPLING;
            
            case IpAttackStateConstant.ATTACKING:
                return TrafficPushStrategy.AGGREGATE;
            
            case IpAttackStateConstant.NORMAL:
            default:
                return TrafficPushStrategy.DELAYED_BATCH;
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

    private void handleRequestSuccess(ServerWebExchange exchange, 
                                    TrafficMonitorDTO monitorDTO, 
                                    long startTime,
                                    TrafficPushStrategy pushStrategy) {
        try {
            long endTime = System.currentTimeMillis();
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 200;
            
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            executePushStrategy(monitorDTO, pushStrategy);

            logger.debug("流量采集完成：{} 耗时{}ms 状态码{} 策略={}", 
                        monitorDTO.getRequestId(), 
                        endTime - startTime, 
                        statusCode,
                        pushStrategy);

        } catch (Exception e) {
            logger.error("处理请求成功回调时发生异常", e);
        }
    }

    private void handleRequestError(ServerWebExchange exchange, 
                                  TrafficMonitorDTO monitorDTO, 
                                  Throwable throwable, 
                                  long startTime,
                                  TrafficPushStrategy pushStrategy) {
        try {
            long endTime = System.currentTimeMillis();
            
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 500;
            
            monitorDTO.markAsFailed(throwable.getMessage());
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            executePushStrategy(monitorDTO, pushStrategy);

            logger.warn("流量采集记录错误：{} 耗时{}ms 错误：{} 状态码{} 策略={}", 
                       monitorDTO.getRequestId(), 
                       endTime - startTime, 
                       throwable.getMessage(),
                       statusCode,
                       pushStrategy);

        } catch (Exception e) {
            logger.error("处理请求错误回调时发生异常", e);
        }
    }

    private void executePushStrategy(TrafficMonitorDTO monitorDTO, TrafficPushStrategy strategy) {
        switch (strategy) {
            case SKIP:
                logger.debug("跳过流量推送: ip={}", monitorDTO.getSourceIp());
                break;
            
            case REALTIME:
                doRealtimePush(monitorDTO);
                break;
            
            case DELAYED_BATCH:
            case COUNTER_ONLY:
            case AGGREGATE:
            case SAMPLING:
                doHandlerPush(monitorDTO, strategy);
                break;
            
            case BATCH:
            default:
                doRealtimePush(monitorDTO);
                break;
        }
    }

    private void doRealtimePush(TrafficMonitorDTO monitorDTO) {
        try {
            trafficClient.pushTraffic(monitorDTO);
            logger.debug("实时推送流量数据成功：请求ID[{}]", monitorDTO.getRequestId());
        } catch (Exception e) {
            logger.warn("实时推送流量数据失败：请求ID[{}], 错误：{}", 
                       monitorDTO.getRequestId(), e.getMessage());
        }
    }

    private void doHandlerPush(TrafficMonitorDTO monitorDTO, TrafficPushStrategy strategy) {
        try {
            var handler = strategyManager.getHandler(strategy);
            if (handler != null) {
                handler.handle(monitorDTO);
            } else {
                logger.warn("未找到策略处理器: {}, 使用实时推送", strategy);
                doRealtimePush(monitorDTO);
            }
        } catch (Exception e) {
            logger.warn("策略处理失败: {}, 错误: {}, 使用实时推送", strategy, e.getMessage());
            doRealtimePush(monitorDTO);
        }
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.TRAFFIC_COLLECT_FILTER_ORDER;
    }

    public String getFilterName() {
        return "TrafficCollectGlobalFilter";
    }

    public int getCachedTrafficCount() {
        return trafficTempCache.getSize();
    }

    public void cleanupExpiredTraffics() {
        trafficTempCache.cleanupExpired();
    }

    public String getStatistics() {
        return String.format("流量采集过滤器 - 缓存流量数:%d 缓存统计:%s\n%s", 
                           getCachedTrafficCount(),
                           trafficTempCache.getStats(),
                           strategyManager.getAllStats());
    }

    @Scheduled(fixedRateString = "${traffic.push.batch-interval-ms:5000}")
    public void flushAllHandlers() {
        try {
            logger.debug("开始定时刷新所有推送处理器");
            strategyManager.flushAll();
        } catch (Exception e) {
            logger.error("定时刷新推送处理器失败", e);
        }
    }
}
