package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.IpAttackStateCache;
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

@Component
public class IpBlacklistDefenseFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(IpBlacklistDefenseFilter.class);

    @Autowired
    private IpBlacklistCache blacklistCache;

    @Autowired
    private MonitorServiceDefenseClient defenseClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());

        try {
            if (blacklistCache.isInBlacklist(sourceIp)) {
                return handleBlacklistedIp(exchange, sourceIp, startTime);
            }

            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("IP黑名单检查过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleBlacklistedIp(ServerWebExchange exchange, String blacklistedIp, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        
        Long expireTimestamp = blacklistCache.getBlacklistExpireTime(blacklistedIp);

        attackStateCache.incrementAttackCount(blacklistedIp);

        boolean skipDefenseLog = attackStateCache.shouldSkipDefenseAction(blacklistedIp);
        String existingEventId = attackStateCache.getEventId(blacklistedIp);
        
        DefenseResultBO defenseResult = new DefenseResultBO(
                DefenseResultBO.DefenseType.BLACKLIST,
                blacklistedIp,
                existingEventId,
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
            if (!skipDefenseLog) {
                DefenseLogDTO defenseLog = DefenseLogUtil.buildDefenseLog(defenseResult);
                defenseClient.pushDefenseLog(defenseLog);
            }
            
            logger.info("拦截黑名单IP请求: IP[{}] URI[{}] 方法[{}] eventId[{}] skipLog={}", 
                       blacklistedIp, 
                       exchange.getRequest().getURI().getPath(),
                       exchange.getRequest().getMethodValue(),
                       existingEventId,
                       skipDefenseLog);

            defenseResult.setSuccessResult(403, "Forbidden - IP Blocked");
            return DefenseResponseUtil.buildIpBlacklistResponse(response, blacklistedIp, defenseResult.getEventId());

        } catch (Exception e) {
            logger.error("处理黑名单IP拦截时发生异常", e);
            defenseResult.setFailureResult(e.getMessage());
            return DefenseResponseUtil.buildIpBlacklistResponse(response, blacklistedIp, defenseResult.getEventId());
        } finally {
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