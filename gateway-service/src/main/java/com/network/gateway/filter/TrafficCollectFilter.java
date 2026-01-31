package com.network.gateway.filter;

import com.network.gateway.dto.TrafficMonitorDTO;
import com.network.gateway.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
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
 * 网关全局流量采集过滤器（完美适配Spring Cloud 2021.0.8 + Spring Boot 2.7.18）
 * 功能：采集请求IP、方法、URI、请求体、响应时间、状态码等信息，标记异常流量
 * 最终修复点：
 * 1. 彻底移除ServerHttpRequest.bufferFactory()，改用ServerHttpResponse获取DataBufferFactory
 * 2. 标准化缓冲区释放逻辑，杜绝内存泄漏
 * 3. 兼容空请求体场景，避免NPE
 */
@Slf4j
@Component
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
        // 核心修复：从Response获取DataBufferFactory（Spring 5.3唯一正确方式）
        DataBufferFactory bufferFactory = response.bufferFactory();

        // 2. 初始化流量DTO，采集请求基础信息
        TrafficMonitorDTO trafficDTO = new TrafficMonitorDTO();
        trafficDTO.setId(UUID.randomUUID().toString());
        trafficDTO.setSourceIp(IpUtil.getSourceIp(request));
        trafficDTO.setTargetServiceIp("127.0.0.1"); // 暂时存网关IP，后续替换为靶场服务IP
        trafficDTO.setMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
        trafficDTO.setUri(request.getPath().value());
        trafficDTO.setQueryParams(request.getQueryParams().toString());
        trafficDTO.setCreateTime(LocalDateTime.now());

        // 3. 响应式读取请求体（Spring 5.3 标准响应式写法，无阻塞、无非法API）
        return DataBufferUtils.join(request.getBody())
                // 修复1：用bufferFactory（从Response获取）创建空缓冲区，替代错误的request.bufferFactory()
                .defaultIfEmpty(bufferFactory.wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    try {
                        // 读取请求体内容
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        String requestBody = new String(bytes, StandardCharsets.UTF_8);
                        trafficDTO.setRequestBody(requestBody);

                        // 修复2：用正确的bufferFactory复制请求体，替代错误的request.bufferFactory()
                        DataBuffer copyBuffer = bufferFactory.wrap(bytes);
                        // 包装请求体，确保后续过滤器可重复读取
                        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(copyBuffer)
                                        .doOnDiscard(DataBuffer.class, DataBufferUtils::release); // 自动释放缓冲区
                            }
                        };

                        // 替换exchange中的请求
                        ServerWebExchange newExchange = exchange.mutate().request(decoratedRequest).build();

                        // 4. 继续执行网关链路，响应返回后补全数据
                        return chain.filter(newExchange)
                                .then(Mono.fromRunnable(() -> {
                                    // 补全响应信息
                                    long responseTime = System.currentTimeMillis() - startTime;
                                    trafficDTO.setResponseTime(responseTime);
                                    trafficDTO.setResponseCode(response.getStatusCode() != null ?
                                            response.getStatusCode().value() : 500);

                                    // 计算请求体字节数，判断异常流量
                                    long requestBodySize = trafficDTO.getRequestBody().getBytes(StandardCharsets.UTF_8).length;
                                    boolean isAbnormal = responseTime > RESPONSE_TIME_THRESHOLD
                                            || requestBodySize > REQUEST_BODY_THRESHOLD;
                                    trafficDTO.setIsAbnormal(isAbnormal);

                                    // 打印采集日志（后续替换为：推送到monitor-service）
                                    log.info("【流量采集】成功采集请求数据：{}", trafficDTO);
                                }));
                    } catch (Exception e) {
                        log.error("【流量采集】读取请求体失败", e);
                        // 读取失败时继续执行链路，避免请求中断
                        return chain.filter(exchange);
                    } finally {
                        // 释放原始缓冲区，避免内存泄漏（Spring 5.3 强制要求）
                        DataBufferUtils.release(dataBuffer);
                    }
                });
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