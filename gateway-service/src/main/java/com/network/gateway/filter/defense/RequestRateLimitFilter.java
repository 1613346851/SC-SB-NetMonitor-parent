package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.RequestRateLimitCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
import com.network.gateway.constant.GatewayCacheConstant;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.dto.DefenseLogDTO;
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

/**
 * 请求限流过滤器
 * 基于时间窗口的请求频率控制，防止单个IP的DDoS攻击
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimitFilter.class);

    @Autowired
    private RequestRateLimitCache rateLimitCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

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
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());

        try {
            // 检查请求频率是否超出限制
            if (isRateLimited(sourceIp)) {
                return handleRateLimitExceeded(exchange, sourceIp, startTime);
            }

            // 请求频率正常，继续执行过滤器链
            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("请求限流检查过程中发生异常，IP: {}", sourceIp, e);
            // 发生异常时继续执行，避免影响正常请求
            return chain.filter(exchange);
        }
    }

    /**
     * 检查IP是否超出请求频率限制
     *
     * @param ip IP地址
     * @return true表示超出限制
     */
    private boolean isRateLimited(String ip) {
        return rateLimitCache.checkAndIncrement(ip, GatewayCacheConstant.RATE_LIMIT_THRESHOLD);
    }

    /**
     * 处理请求频率超限的情况
     *
     * @param exchange ServerWebExchange对象
     * @param sourceIp 源IP
     * @param startTime 开始时间
     * @return Mono<Void>
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, String sourceIp, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        int currentCount = rateLimitCache.getCurrentRequestCount(sourceIp);
        
        // 构建防御结果
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.RATE_LIMIT,
                sourceIp,
                "RATE_LIMIT_EVENT_" + System.currentTimeMillis(),
                String.format("请求频率过高(%d次/秒 > %d次/秒)", 
                            currentCount, GatewayCacheConstant.RATE_LIMIT_THRESHOLD)
        );
        
        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(currentCount > GatewayCacheConstant.RATE_LIMIT_THRESHOLD * 2 ? 
                                 DefenseResultBO.RiskLevel.HIGH : DefenseResultBO.RiskLevel.MEDIUM);

        try {
            // 记录防御日志
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            logger.warn("限流拦截请求: IP[{}] 当前频率{}次/秒 URI[{}] 方法[{}]", 
                       sourceIp, currentCount,
                       exchange.getRequest().getURI().getPath(),
                       exchange.getRequest().getMethodValue());

            // 构建并返回限流响应
            defenseResult.setSuccessResult(429, "Too Many Requests");
            return DefenseResponseUtil.buildRateLimitResponse(
                    response, sourceIp, GatewayCacheConstant.RATE_LIMIT_THRESHOLD);

        } catch (Exception e) {
            logger.error("处理限流拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            
            // 即使日志推送失败，也要返回限流响应
            return DefenseResponseUtil.buildRateLimitResponse(
                    response, sourceIp, GatewayCacheConstant.RATE_LIMIT_THRESHOLD);
        } finally {
            // 记录处理耗时
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
            logger.debug("限流防御执行完成: {}", DefenseLogUtil.buildExecutionSummary(defenseResult));
        }
    }

    /**
     * 获取过滤器优先级
     *
     * @return 优先级数值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.REQUEST_RATE_LIMIT_FILTER_ORDER;
    }

    /**
     * 获取过滤器名称
     *
     * @return 过滤器名称
     */
    public String getFilterName() {
        return "RequestRateLimitFilter";
    }

    /**
     * 获取当前活跃的IP数量
     *
     * @return 活跃IP数量
     */
    public int getActiveIpCount() {
        return rateLimitCache.getSize();
    }

    /**
     * 获取指定IP的当前请求计数
     *
     * @param ip IP地址
     * @return 当前请求数
     */
    public int getCurrentRequestCount(String ip) {
        return rateLimitCache.getCurrentRequestCount(ip);
    }

    /**
     * 重置指定IP的请求计数
     *
     * @param ip IP地址
     */
    public void resetRequestCount(String ip) {
        rateLimitCache.resetRequestCount(ip);
    }

    /**
     * 清理过期的限流记录
     */
    public void cleanupExpiredRecords() {
        rateLimitCache.cleanupExpired();
    }

    /**
     * 获取高频请求的IP列表
     *
     * @param ratio 阈值比例（相对于限流阈值）
     * @return 高频IP集合
     */
    public java.util.Set<String> getHighFrequencyIps(double ratio) {
        return rateLimitCache.getHighFrequencyIps(GatewayCacheConstant.RATE_LIMIT_THRESHOLD, ratio);
    }

    /**
     * 获取限流统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        RequestRateLimitCache.RateLimitStatistics stats = 
                rateLimitCache.getStatistics(GatewayCacheConstant.RATE_LIMIT_THRESHOLD);
        return String.format("请求限流过滤器 - %s", stats.toString());
    }

    /**
     * 批量重置多个IP的请求计数
     *
     * @param ips IP地址集合
     * @return 重置的IP数量
     */
    public int batchResetRequestCounts(java.util.Set<String> ips) {
        return rateLimitCache.batchResetRequestCount(ips);
    }
}