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
                logger.debug("跳过流量采集: {}", exchange.getRequest().getURI().getPath());
                return chain.filter(exchange);
            }

            // 提取原始流量信息
            RawTrafficBO rawTraffic = ServerWebExchangeUtil.extractTrafficInfo(exchange);
            logger.debug("开始采集流量: {}", rawTraffic.getTrafficSummary());

            // 预处理流量数据
            TrafficMonitorDTO monitorDTO = TrafficPreProcessUtil.preprocessTraffic(rawTraffic);

            // 存储到临时缓存
            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            // 异步推送流量数据到监控服务
            pushTrafficAsync(monitorDTO);

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

        // 可以添加更多的跳过条件
        String path = exchange.getRequest().getURI().getPath();
        
        // 跳过静态资源请求
        if (path.endsWith(".css") || path.endsWith(".js") || 
            path.endsWith(".png") || path.endsWith(".jpg") || 
            path.endsWith(".ico")) {
            return true;
        }

        return false;
    }

    /**
     * 异步推送流量数据到监控服务
     *
     * @param monitorDTO 流量监控DTO
     */
    private void pushTrafficAsync(TrafficMonitorDTO monitorDTO) {
        // 使用异步方式推送，避免阻塞主流程
        Mono.fromRunnable(() -> {
            try {
                trafficClient.pushTraffic(monitorDTO);
                logger.debug("异步推送流量数据成功: 请求ID[{}]", monitorDTO.getRequestId());
            } catch (Exception e) {
                logger.warn("异步推送流量数据失败: 请求ID[{}], 错误: {}", 
                           monitorDTO.getRequestId(), e.getMessage());
                // 失败重试机制（简化版）
                retryPushTraffic(monitorDTO);
            }
        }).subscribe();
    }

    /**
     * 失败重试推送流量数据
     *
     * @param monitorDTO 流量监控DTO
     */
    private void retryPushTraffic(TrafficMonitorDTO monitorDTO) {
        try {
            // 简单的一次重试机制
            Thread.sleep(1000); // 等待1秒后重试
            trafficClient.pushTraffic(monitorDTO);
            logger.info("重试推送流量数据成功: 请求ID[{}]", monitorDTO.getRequestId());
        } catch (Exception retryException) {
            logger.error("重试推送流量数据也失败: 请求ID[{}], 错误: {}", 
                        monitorDTO.getRequestId(), retryException.getMessage());
        }
    }

    /**
     * 处理请求成功的情况
     *
     * @param exchange ServerWebExchange对象
     * @param monitorDTO 流量监控DTO
     * @param startTime 开始时间
     */
    private void handleRequestSuccess(ServerWebExchange exchange, 
                                    TrafficMonitorDTO monitorDTO, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                           exchange.getResponse().getStatusCode().value() : 200;
            
            // 更新响应信息
            monitorDTO.setResponseInfo(endTime, statusCode, 
                                     ServerWebExchangeUtil.getContentLength(exchange));

            // 重新存储到缓存（更新响应信息）
            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            logger.debug("流量采集完成: {} 耗时{}ms 状态码{}", 
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
     * @param exchange ServerWebExchange对象
     * @param monitorDTO 流量监控DTO
     * @param throwable 异常对象
     * @param startTime 开始时间
     */
    private void handleRequestError(ServerWebExchange exchange, 
                                  TrafficMonitorDTO monitorDTO, 
                                  Throwable throwable, long startTime) {
        try {
            long endTime = System.currentTimeMillis();
            
            // 标记为处理失败
            monitorDTO.markAsFailed(throwable.getMessage());
            monitorDTO.setResponseInfo(endTime, 500, 0L);

            // 重新存储到缓存
            trafficTempCache.putTraffic(monitorDTO.getRequestId(), monitorDTO);

            logger.warn("流量采集记录错误: {} 耗时{}ms 错误: {}", 
                       monitorDTO.getRequestId(), 
                       endTime - startTime, 
                       throwable.getMessage());

        } catch (Exception e) {
            logger.error("处理请求错误回调时发生异常", e);
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