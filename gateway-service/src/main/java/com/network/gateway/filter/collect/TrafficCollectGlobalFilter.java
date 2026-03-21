package com.network.gateway.filter.collect;

import com.network.gateway.bo.RawTrafficBO;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.TrafficTempCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.exception.GatewayBizException;
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

            boolean skipTrafficPush = attackStateCache.shouldSkipTrafficPush(sourceIp);
            if (skipTrafficPush) {
                logger.debug("IP处于DEFENDED状态，跳过流量推送: ip={}", sourceIp);
            }

            attackStateCache.incrementRequestCount(sourceIp);
            
            logger.debug("开始采集流量：{}", rawTraffic.getTrafficSummary());

            TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);
            monitorDTO.setSkipPush(skipTrafficPush);

            trafficTempCache.putTraffic(requestId, monitorDTO);

            return chain.filter(exchange)
                    .doOnSuccess(unused -> {
                        handleRequestSuccess(exchange, monitorDTO, startTime);
                    })
                    .doOnError(throwable -> {
                        handleRequestError(exchange, monitorDTO, throwable, startTime);
                    });

        } catch (Exception e) {
            logger.error("流量采集过程中发生异常", e);
            return chain.filter(exchange);
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
                                    TrafficMonitorDTO monitorDTO, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 200;
            
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            if (!monitorDTO.isSkipPush()) {
                pushTraffic(monitorDTO);
            }

            logger.debug("流量采集完成：{} 耗时{}ms 状态码{} skipPush={}", 
                        monitorDTO.getRequestId(), 
                        endTime - startTime, 
                        statusCode,
                        monitorDTO.isSkipPush());

        } catch (Exception e) {
            logger.error("处理请求成功回调时发生异常", e);
        }
    }

    private void handleRequestError(ServerWebExchange exchange, 
                                  TrafficMonitorDTO monitorDTO, 
                                  Throwable throwable, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 500;
            
            monitorDTO.markAsFailed(throwable.getMessage());
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            if (!monitorDTO.isSkipPush()) {
                pushTraffic(monitorDTO);
            }

            logger.warn("流量采集记录错误：{} 耗时{}ms 错误：{} 状态码{} skipPush={}", 
                       monitorDTO.getRequestId(), 
                       endTime - startTime, 
                       throwable.getMessage(),
                       statusCode,
                       monitorDTO.isSkipPush());

        } catch (Exception e) {
            logger.error("处理请求错误回调时发生异常", e);
        }
    }

    private void pushTraffic(TrafficMonitorDTO monitorDTO) {
        try {
            trafficClient.pushTraffic(monitorDTO);
            logger.debug("推送流量数据成功：请求 ID[{}]", monitorDTO.getRequestId());
        } catch (Exception e) {
            logger.warn("推送流量数据失败：请求 ID[{}], 错误：{}", 
                       monitorDTO.getRequestId(), e.getMessage());
        }
    }

    /**
     * 获取过滤器优先级
     *
     * @return 优先级数值（越小优先级越高）
     */
    @Override
    public int getOrder() {
        // 设置为最高优先级，确保最先执行
        return GatewayFilterOrderConstant.TRAFFIC_COLLECT_FILTER_ORDER;
    }

    /**
     * 获取过滤器名称
     *
     * @return 过滤器名称
     */
    public String getFilterName() {
        return "TrafficCollectGlobalFilter";
    }

    /**
     * 获取当前缓存中的流量数量
     *
     * @return 流量数量
     */
    public int getCachedTrafficCount() {
        return trafficTempCache.getSize();
    }

    /**
     * 清理过期的流量缓存
     */
    public void cleanupExpiredTraffics() {
        trafficTempCache.cleanupExpired();
    }

    /**
     * 获取过滤器统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("流量采集过滤器 - 缓存流量数:%d 缓存统计:%s", 
                           getCachedTrafficCount(), 
                           trafficTempCache.getStats());
    }
}