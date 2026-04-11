package com.network.gateway.filter.defense;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.cache.GatewayConfigCache;
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

/**
 * IP黑名单防御过滤器
 * 使用动态配置获取黑名单开关
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

    @Autowired
    private IpAttackStateCache attackStateCache;

    @Autowired
    private GatewayConfigCache configCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());

        try {
            if (!isBlacklistDefenseEnabled()) {
                return chain.filter(exchange);
            }

            if (ServerWebExchangeUtil.isStaticResource(exchange)) {
                return chain.filter(exchange);
            }

            if (ServerWebExchangeUtil.isHealthCheck(exchange) || ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
                return chain.filter(exchange);
            }

            if (blacklistCache.isInBlacklist(sourceIp)) {
                return handleBlacklistedIp(exchange, sourceIp, startTime);
            }

            return chain.filter(exchange);

        } catch (Exception e) {
            logger.error("IP黑名单检查过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    private boolean isBlacklistDefenseEnabled() {
        return configCache.isDefenseEnabled("blacklist");
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
            defenseResult.setSuccessResult(403, "Forbidden - IP Blocked");
            
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

    @Override
    public int getOrder() {
        return GatewayFilterOrderConstant.IP_BLACKLIST_DEFENSE_FILTER_ORDER;
    }

    public String getFilterName() {
        return "IpBlacklistDefenseFilter";
    }

    public int getBlacklistSize() {
        return blacklistCache.getSize();
    }

    public boolean addToBlacklist(String ip, Long expireTime) {
        return blacklistCache.addToBlacklist(ip, expireTime);
    }

    public boolean removeFromBlacklist(String ip) {
        return blacklistCache.removeFromBlacklist(ip);
    }

    public void cleanupExpiredBlacklist() {
        blacklistCache.cleanupExpired();
    }

    public String getStatistics() {
        return String.format("IP黑名单过滤器 - 黑名单数量:%d 开关:%s 缓存统计:%s", 
                getBlacklistSize(),
                isBlacklistDefenseEnabled() ? "开启" : "关闭",
                blacklistCache.getStats());
    }
}
