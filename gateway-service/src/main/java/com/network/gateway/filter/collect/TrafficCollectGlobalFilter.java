package com.network.gateway.filter.collect;

import com.network.gateway.bo.RawTrafficBO;
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

/**
 * 流量采集全局过滤器
 * 网关的核心过滤器，负责采集全量请求信息并推送到监控服务
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class TrafficCollectGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TrafficCollectGlobalFilter.class);

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private TrafficTempCache trafficTempCache;

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
        
        try {
            // 检查是否需要跳过采集（健康检查、管理端点等）
            if (shouldSkipCollection(exchange)) {
                logger.debug("跳过流量采集：{}", exchange.getRequest().getURI().getPath());
                return chain.filter(exchange);
            }

            // 检查是否已经采集过该请求（防止重复采集）
            RawTrafficBO rawTraffic = ServerWebExchangeUtil.extractTrafficInfo(exchange);
            String requestId = rawTraffic.getRequestId();
            
            if (trafficTempCache.containsTraffic(requestId)) {
                logger.debug("请求已采集，跳过：{}", requestId);
                return chain.filter(exchange);
            }
            
            logger.debug("开始采集流量：{}", rawTraffic.getTrafficSummary());

            // 预处理流量数据
            TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);

            // 存储到临时缓存（仅存储，不推送）
            trafficTempCache.putTraffic(requestId, monitorDTO);

            // 继续执行过滤器链（非阻塞）
            return chain.filter(exchange)
                    .doOnSuccess(unused -> {
                        // 请求成功处理后的回调
                        handleRequestSuccess(exchange, monitorDTO, startTime);
                    })
                    .doOnError(throwable -> {
                        // 请求处理失败的回调
                        handleRequestError(exchange, monitorDTO, throwable, startTime);
                    });

        } catch (Exception e) {
            logger.error("流量采集过程中发生异常", e);
            // 发生异常时仍然继续执行过滤器链
            return chain.filter(exchange);
        }
    }

    /**
     * 判断是否应该跳过流量采集
     *
     * @param exchange ServerWebExchange对象
     * @return true表示应该跳过
     */
    private boolean shouldSkipCollection(ServerWebExchange exchange) {
        // 跳过健康检查请求
        if (ServerWebExchangeUtil.isHealthCheck(exchange)) {
            return true;
        }

        // 跳过管理端点请求
        if (ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
            return true;
        }

        // 跳过静态资源请求
        if (ServerWebExchangeUtil.isStaticResource(exchange)) {
            return true;
        }

        return false;
    }

    /**
     * 处理请求成功的情况
     *
     * @param exchange ServerWebExchange 对象
     * @param monitorDTO 流量监控 DTO
     * @param startTime 开始时间
     */
    private void handleRequestSuccess(ServerWebExchange exchange, 
                                    TrafficMonitorDTO monitorDTO, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 200;
            
            // 更新响应信息
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            // 更新缓存
            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            // 推送完整的流量数据（包含响应信息）
            pushTraffic(monitorDTO);

            logger.debug("流量采集完成：{} 耗时{}ms 状态码{}", 
                        monitorDTO.getRequestId(), 
                        endTime - startTime, 
                        statusCode);

        } catch (Exception e) {
            logger.error("处理请求成功回调时发生异常", e);
        }
    }

    /**
     * 处理请求失败的情况
     *
     * @param exchange ServerWebExchange 对象
     * @param monitorDTO 流量监控 DTO
     * @param throwable 异常对象
     * @param startTime 开始时间
     */
    private void handleRequestError(ServerWebExchange exchange, 
                                  TrafficMonitorDTO monitorDTO, 
                                  Throwable throwable, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            
            // 确定实际的状态码
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 500;
            
            // 标记为处理失败
            monitorDTO.markAsFailed(throwable.getMessage());
            monitorDTO.setResponseInfo(statusCode, "", endTime - startTime);

            // 更新缓存
            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            // 推送完整的流量数据（包含响应信息）
            pushTraffic(monitorDTO);

            logger.warn("流量采集记录错误：{} 耗时{}ms 错误：{} 状态码{}", 
                       monitorDTO.getRequestId(), 
                       endTime - startTime, 
                       throwable.getMessage(),
                       statusCode);

        } catch (Exception e) {
            logger.error("处理请求错误回调时发生异常", e);
        }
    }

    /**
     * 推送流量数据到监控服务
     *
     * @param monitorDTO 流量监控 DTO
     */
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