package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.IpBlacklistCache;
import com.network.gateway.client.MonitorServiceDefenseClient;
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
 * IP黑名单防御过滤器
 * 检查请求源IP是否在黑名单中，如果是则阻止访问
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class IpBlacklistDefenseFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(IpBlacklistDefenseFilter.class);

    @Autowired
    private IpBlacklistCache blacklistCache;

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
            // 检查IP是否在黑名单中
            if (blacklistCache.isInBlacklist(sourceIp)) {
                return handleBlacklistedIp(exchange, sourceIp, startTime);
            }

            // IP不在黑名单中，继续执行过滤器链
            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("IP黑名单检查过程中发生异常，IP: {}", sourceIp, e);
            // 发生异常时继续执行，避免影响正常请求
            return chain.filter(exchange);
        }
    }

    /**
     * 处理被列入黑名单的IP
     *
     * @param exchange ServerWebExchange对象
     * @param blacklistedIp 黑名单IP
     * @param startTime 开始时间
     * @return Mono<Void>
     */
    private Mono<Void> handleBlacklistedIp(ServerWebExchange exchange, String blacklistedIp, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        
        Long expireTimestamp = blacklistCache.getBlacklistExpireTime(blacklistedIp);
        
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.BLACKLIST,
                blacklistedIp,
                "BLACKLIST_EVENT_" + System.currentTimeMillis(),
                "IP在黑名单中"
        );
        
        defenseResult.setExpireTimestamp(expireTimestamp);
        defenseResult.setRequestInfo(
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                ServerWebExchangeUtil.extractUserAgent(ServerWebExchangeUtil.extractHeaders(exchange.getRequest()))
        );
        defenseResult.setRiskLevel(DefenseResultBO.RiskLevel.HIGH);

        try {
            // 记录防御日志
            DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
            defenseClient.pushDefenseLog(defenseLog);
            
            logger.info("拦截黑名单IP请求: IP[{}] URI[{}] 方法[{}]", 
                       blacklistedIp, 
                       exchange.getRequest().getURI().getPath(),
                       exchange.getRequest().getMethodValue());

            // 构建并返回拦截响应
            defenseResult.setSuccessResult(403, "Forbidden - IP Blocked");
            return DefenseResponseUtil.buildIpBlacklistResponse(response, blacklistedIp, defenseResult.getEventId());

        } catch (Exception e) {
            logger.error("处理黑名单IP拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            
            // 即使日志推送失败，也要返回拦截响应
            return DefenseResponseUtil.buildIpBlacklistResponse(response, blacklistedIp, defenseResult.getEventId());
        } finally {
            // 记录处理耗时
            defenseResult.setProcessingTime(System.currentTimeMillis() - startTime);
            logger.debug("黑名单防御执行完成: {}", DefenseLogUtil.buildExecutionSummary(defenseResult));
        }
    }

    /**
     * 获取过滤器优先级
     *
     * @return 优先级数值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.IP_BLACKLIST_DEFENSE_FILTER_ORDER;
    }

    /**
     * 获取过滤器名称
     *
     * @return 过滤器名称
     */
    public String getFilterName() {
        return "IpBlacklistDefenseFilter";
    }

    /**
     * 获取黑名单缓存大小
     *
     * @return 黑名单数量
     */
    public int getBlacklistSize() {
        return blacklistCache.getSize();
    }

    /**
     * 手动添加IP到黑名单
     *
     * @param ip IP地址
     * @param expireTime 过期时间戳
     * @return true表示添加成功
     */
    public boolean addToBlacklist(String ip, Long expireTime) {
        return blacklistCache.addToBlacklist(ip, expireTime);
    }

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址
     * @return true表示移除成功
     */
    public boolean removeFromBlacklist(String ip) {
        return blacklistCache.removeFromBlacklist(ip);
    }

    /**
     * 清理过期的黑名单条目
     */
    public void cleanupExpiredBlacklist() {
        blacklistCache.cleanupExpired();
    }

    /**
     * 获取过滤器统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("IP黑名单过滤器 - 黑名单数量:%d 缓存统计:%s", 
                           getBlacklistSize(), 
                           blacklistCache.getStats());
    }
}