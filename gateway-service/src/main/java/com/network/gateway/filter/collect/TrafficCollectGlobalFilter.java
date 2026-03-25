package com.network.gateway.filter.collect;

import com.network.gateway.bo.RawTrafficBO;
import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.TrafficAggregate;
import com.network.gateway.cache.TrafficAggregateCache;
import com.network.gateway.cache.TrafficTempCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.enums.TrafficPushStrategy;
import com.network.gateway.exception.GatewayBizException;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TrafficCollectGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TrafficCollectGlobalFilter.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private TrafficTempCache trafficTempCache;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private TrafficAggregateCache aggregateCache;

    private final ConcurrentMap<String, AtomicInteger> samplingCounters = new ConcurrentHashMap<>();

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
                return TrafficPushStrategy.BATCH;
            
            case IpAttackStateConstant.SUSPICIOUS:
            case IpAttackStateConstant.COOLDOWN:
                return TrafficPushStrategy.SAMPLING;
            
            case IpAttackStateConstant.ATTACKING:
                return TrafficPushStrategy.BATCH;
            
            case IpAttackStateConstant.NORMAL:
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
                logger.debug("DEFENDED状态，跳过流量推送: ip={}", monitorDTO.getSourceIp());
                break;
            
            case SAMPLING:
                doSamplingPush(monitorDTO);
                break;
            
            case BATCH:
                doBatchPush(monitorDTO);
                break;
            
            case REALTIME:
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

    private void doSamplingPush(TrafficMonitorDTO monitorDTO) {
        String sourceIp = monitorDTO.getSourceIp();
        int samplingRate = getSamplingRate();
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(sourceIp));
        
        TrafficAggregate aggregate = aggregateCache.getOrAdd(monitorDTO, stateTag);
        int currentCount = aggregate.increment();
        aggregate.addProcessingTime(monitorDTO.getProcessingTime() != null ? monitorDTO.getProcessingTime() : 0);
        
        if (monitorDTO.getResponseStatus() != null && monitorDTO.getResponseStatus() >= 400) {
            aggregate.incrementError();
        }
        
        if (currentCount % samplingRate == 1) {
            try {
                monitorDTO.setRequestCount(currentCount);
                monitorDTO.setStateTag(stateTag);
                monitorDTO.setIsAggregated(true);
                monitorDTO.setAggregateStartTime(formatDateTime(aggregate.getStartTime()));
                monitorDTO.setAggregateEndTime(formatDateTime(aggregate.getLastUpdateTime()));
                monitorDTO.setErrorCount(aggregate.getErrorCount());
                monitorDTO.setAvgProcessingTime(aggregate.getAverageProcessingTime());
                
                trafficClient.pushTraffic(monitorDTO);
                logger.debug("采样推送流量数据成功：请求ID[{}] 采样率=1/{} 当前计数={}", 
                    monitorDTO.getRequestId(), samplingRate, currentCount);
            } catch (Exception e) {
                logger.warn("采样推送流量数据失败：请求ID[{}], 错误：{}", 
                           monitorDTO.getRequestId(), e.getMessage());
            }
        } else {
            logger.debug("采样推送跳过：请求ID[{}] 当前计数={} 采样率=1/{}", 
                monitorDTO.getRequestId(), currentCount, samplingRate);
        }
    }

    private void doBatchPush(TrafficMonitorDTO monitorDTO) {
        String sourceIp = monitorDTO.getSourceIp();
        String stateTag = IpAttackStateConstant.getStateName(attackStateCache.getState(sourceIp));
        long processingTime = monitorDTO.getProcessingTime() != null ? monitorDTO.getProcessingTime() : 0;
        boolean isError = monitorDTO.getResponseStatus() != null && monitorDTO.getResponseStatus() >= 400;
        
        aggregateCache.incrementCount(monitorDTO, stateTag, processingTime, isError);
        
        logger.debug("批量推送模式：流量数据已聚合缓存 请求ID[{}] ip={}", monitorDTO.getRequestId(), sourceIp);
    }

    private int getSamplingRate() {
        return configCache.getTrafficPushSamplingRate();
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

    public void cleanupSamplingCounters() {
        samplingCounters.clear();
        logger.debug("采样计数器已清理");
    }

    public String getStatistics() {
        return String.format("流量采集过滤器 - 缓存流量数:%d 采样IP数:%d 缓存统计:%s 聚合统计:%s", 
                           getCachedTrafficCount(),
                           samplingCounters.size(),
                           trafficTempCache.getStats(),
                           aggregateCache.getStats());
    }

    @Scheduled(fixedRateString = "${traffic.push.batch-interval-ms:5000}")
    public void flushBatchQueue() {
        try {
            List<TrafficAggregate> aggregates = aggregateCache.flushExpired();
            
            if (aggregates.isEmpty()) {
                return;
            }
            
            logger.info("开始批量推送聚合流量数据: 共{}条", aggregates.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (TrafficAggregate aggregate : aggregates) {
                try {
                    TrafficMonitorDTO dto = convertAggregateToDTO(aggregate);
                    trafficClient.pushTraffic(dto);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    logger.warn("批量推送聚合流量失败: ip={}, uri={}, error={}", 
                        aggregate.getSourceIp(), aggregate.getRequestUri(), e.getMessage());
                }
            }
            
            logger.info("批量推送聚合流量完成: 成功{}条, 失败{}条", successCount, failCount);
            
        } catch (Exception e) {
            logger.error("批量推送聚合流量异常", e);
        }
    }

    private TrafficMonitorDTO convertAggregateToDTO(TrafficAggregate aggregate) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        dto.setSourceIp(aggregate.getSourceIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri(aggregate.getRequestUri());
        dto.setHttpMethod(aggregate.getHttpMethod());
        dto.setUserAgent(aggregate.getUserAgent());
        dto.setContentType(aggregate.getContentType());
        dto.setRequestCount(aggregate.getCount());
        dto.setErrorCount(aggregate.getErrorCount());
        dto.setAvgProcessingTime(aggregate.getAverageProcessingTime());
        dto.setStateTag(aggregate.getStateTag());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(formatDateTime(aggregate.getStartTime()));
        dto.setAggregateEndTime(formatDateTime(aggregate.getLastUpdateTime()));
        dto.setRequestId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatDateTime(LocalDateTime.now()));
        dto.setSuccess(true);
        dto.setResponseStatus(200);
        return dto;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
