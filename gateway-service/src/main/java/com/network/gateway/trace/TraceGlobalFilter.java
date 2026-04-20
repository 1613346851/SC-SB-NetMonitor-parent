package com.network.gateway.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TraceGlobalFilter implements GlobalFilter, Ordered {

    private final TraceContext traceContext;
    private final TraceService traceService;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        String uri = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        
        String existingTraceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        String traceId;
        
        if (existingTraceId != null && !existingTraceId.isEmpty()) {
            traceContext.startTrace(ip, existingTraceId);
            traceId = existingTraceId;
            log.debug("Continuing existing trace: traceId={}", traceId);
        } else {
            traceId = traceService.startTrace(ip, uri, method);
            traceContext.startTrace(ip, traceId);
            log.debug("Starting new trace: traceId={}", traceId);
        }
        
        String spanId = traceContext.getSpanId();
        traceService.addSpan(traceId, spanId, "GATEWAY_REQUEST", null);
        
        long startTime = System.currentTimeMillis();
        
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-Trace-Id", traceId)
            .header("X-Span-Id", spanId)
            .header("X-Client-Ip", ip)
            .build();
        
        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("uri", uri);
        requestDetails.put("method", method);
        requestDetails.put("ip", ip);
        requestDetails.put("userAgent", exchange.getRequest().getHeaders().getFirst("User-Agent"));
        traceService.addEvent(traceId, "REQUEST_RECEIVED", "Request received at gateway", requestDetails);
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .doOnSuccess(v -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null 
                    ? exchange.getResponse().getStatusCode().value() : 200;
                
                String status = statusCode < 400 ? "SUCCESS" : "ERROR";
                traceService.endSpan(traceId, spanId, status, null);
                traceService.endTrace(traceId, status, statusCode, duration);
                
                if (statusCode >= 400) {
                    Map<String, Object> errorDetails = new HashMap<>();
                    errorDetails.put("statusCode", statusCode);
                    errorDetails.put("duration", duration);
                    traceService.addEvent(traceId, "RESPONSE_ERROR", "Response with error status", errorDetails);
                }
                
                log.debug("Trace completed: traceId={}, status={}, duration={}ms", traceId, status, duration);
            })
            .doOnError(e -> {
                long duration = System.currentTimeMillis() - startTime;
                traceService.endSpan(traceId, spanId, "ERROR", e.getMessage());
                traceService.endTrace(traceId, "ERROR", 500, duration);
                
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("error", e.getClass().getSimpleName());
                errorDetails.put("message", e.getMessage());
                errorDetails.put("duration", duration);
                traceService.addEvent(traceId, "REQUEST_ERROR", "Request processing error", errorDetails);
                
                log.error("Trace error: traceId={}, error={}", traceId, e.getMessage());
            })
            .doFinally(signalType -> {
                traceContext.endTrace();
            });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
