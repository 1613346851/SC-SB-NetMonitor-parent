package com.network.gateway.filter.defense;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.constant.GatewayFilterOrderConstant;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.util.ServerWebExchangeUtil;
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
public class DefenseStateCheckFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefenseStateCheckFilter.class);

    @Autowired
    private IpAttackStateCache attackStateCache;

    public DefenseStateCheckFilter() {
    }

    public DefenseStateCheckFilter(IpAttackStateCache attackStateCache) {
        this.attackStateCache = attackStateCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());
        
        try {
            if (ServerWebExchangeUtil.isHealthCheck(exchange) || 
                ServerWebExchangeUtil.isManagementEndpoint(exchange)) {
                return chain.filter(exchange);
            }
            
            int currentState = attackStateCache.getState(sourceIp);
            
            if (currentState == IpAttackStateConstant.DEFENDED) {
                logger.debug("IP处于DEFENDED状态，已跳过限流检测: ip={}", sourceIp);
                exchange.getAttributes().put("defense_state", "DEFENDED");
                exchange.getAttributes().put("skip_rate_limit", true);
            } else if (currentState == IpAttackStateConstant.COOLDOWN) {
                logger.debug("IP处于COOLDOWN状态: ip={}", sourceIp);
                exchange.getAttributes().put("defense_state", "COOLDOWN");
            } else if (currentState == IpAttackStateConstant.SUSPICIOUS) {
                logger.debug("IP处于SUSPICIOUS状态: ip={}", sourceIp);
                exchange.getAttributes().put("defense_state", "SUSPICIOUS");
            } else if (currentState == IpAttackStateConstant.ATTACKING) {
                logger.debug("IP处于ATTACKING状态: ip={}", sourceIp);
                exchange.getAttributes().put("defense_state", "ATTACKING");
            }
            
            return chain.filter(exchange);
            
        } catch (Exception e) {
            logger.error("防御状态检查过程中发生异常，IP: {}", sourceIp, e);
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return 6;
    }

    public String getFilterName() {
        return "DefenseStateCheckFilter";
    }
}
