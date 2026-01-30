package com.network.gateway.filter;

import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 网关全局流量采集过滤器（适配Spring Cloud 2021.0.8）
 * 功能：采集请求IP、方法、URI、请求体、响应时间、状态码等信息，标记异常流量
 */
@Slf4j
@Component // 必须交给Spring容器，网关才会自动加载
public class TrafficCollectFilter implements GlobalFilter, Ordered {

    // 异常流量阈值：响应时间>3000ms 或 请求体>100KB（字节）
    private static final long RESPONSE_TIME_THRESHOLD = 3000L;
    private static final long REQUEST_BODY_THRESHOLD = 1024 * 100L;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 记录请求开始时间（用于计算响应时间）
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 2. 初始化流量DTO，采集请求基础信息（避免空指针）
        TrafficMonitorDTO trafficDTO = new TrafficMonitorDTO();
        trafficDTO.setId(UUID.randomUUID().toString());
        trafficDTO.setSourceIp(IpUtil.getSourceIp(request));
        trafficDTO.setTargetServiceIp("127.0.0.1"); // 暂时存网关IP，后续替换为靶场服务IP
        // 修复：处理request.getMethod()为null的情况
        trafficDTO.setMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
        trafficDTO.setUri(request.getPath().value());
        trafficDTO.setQueryParams(request.getQueryParams().toString());
        trafficDTO.setCreateTime(LocalDateTime.now());

        // 3. 读取并复制请求体（核心修复：适配2021.0.8版本的流复制写法）
        String requestBody = "";
        try {
            // 读取原始请求体
            DataBuffer buffer = request.getBody().blockFirst();
            if (buffer != null) {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                requestBody = new String(bytes, StandardCharsets.UTF_8);

                // 修复：复制请求体流，用ServerHttpRequestDecorator包装（当前版本标准写法）
                DataBuffer copyBuffer = buffer.factory().wrap(bytes);
                ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        return Flux.just(copyBuffer);
                    }
                };
                // 替换exchange中的请求为装饰后的请求（关键：让后续过滤器能读取到流）
                exchange = exchange.mutate().request(decoratedRequest).build();
            }
        } catch (Exception e) {
            log.error("【流量采集】读取请求体失败", e);
        }
        trafficDTO.setRequestBody(requestBody);

        // 4. 继续执行网关链路（转发请求），响应返回后补全数据
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // 补全响应信息（处理空指针）
                    long responseTime = System.currentTimeMillis() - startTime;
                    trafficDTO.setResponseTime(responseTime);
                    trafficDTO.setResponseCode(response.getStatusCode() != null ? response.getStatusCode().value() : 500);

                    // 计算请求体字节数，判断异常流量
                    long requestBodySize = trafficDTO.getRequestBody().getBytes(StandardCharsets.UTF_8).length;
                    boolean isAbnormal = responseTime > RESPONSE_TIME_THRESHOLD || requestBodySize > REQUEST_BODY_THRESHOLD;
                    trafficDTO.setIsAbnormal(isAbnormal);

                    // 打印采集日志（后续替换为：推送到monitor-service）
                    log.info("【流量采集】成功采集请求数据：{}", trafficDTO);
                }));
    }

    /**
     * 过滤器执行顺序：数字越小，执行越早
     * -100：确保在网关路由转发前执行，优先采集请求数据
     */
    @Override
    public int getOrder() {
        return -100;
    }
}