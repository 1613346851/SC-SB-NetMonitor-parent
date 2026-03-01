package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

/**
 * DDoS攻击目标模拟接口（资源消耗型）
 * 核心：模拟一个易被洪水请求耗尽的业务接口，作为DDoS攻击的靶子。
 * 定位：与 SqlVulnController, XssVulnController 一致，均为被攻击对象。
 */
@RestController
@RequestMapping("/target/ddos")
@Slf4j
public class DdosTargetController {

    // 总请求计数器（模拟接口被访问的次数）
    private final AtomicLong totalRequestCount = new AtomicLong(0);
    // 模拟一个“昂贵”的操作，例如计算斐波那契数列，以消耗CPU和延长响应时间
    private static final int EXPENSIVE_CALCULATION_N = 35;

    /**
     * 接口1：模拟CPU密集型计算接口（易受洪水攻击）
     * 攻击场景：攻击者高频调用此接口，耗尽服务器CPU资源。
     */
    @GetMapping("/compute-heavy")
    public String computeHeavyTask() {
        long startTime = System.currentTimeMillis();
        long requestId = totalRequestCount.incrementAndGet();

        log.warn("【DDoS攻击目标-CPU密集型】收到请求 #{}，开始模拟高负载计算 (斐波那契{})...", requestId, EXPENSIVE_CALCULATION_N);

        // 模拟一个消耗CPU的计算任务（例如计算斐波那契数列）
        long result = fibonacci(EXPENSIVE_CALCULATION_N);

        long costTime = System.currentTimeMillis() - startTime;

        String response = String.format(
                "{\n" +
                        "  \"code\": 200,\n" +
                        "  \"msg\": \"CPU密集型计算完成\",\n" +
                        "  \"data\": {\n" +
                        "    \"request_id\": %d,\n" +
                        "    \"calculation\": \"fibonacci(%d)\",\n" +
                        "    \"result\": %d,\n" +
                        "    \"cost_time_ms\": %d,\n" +
                        "    \"server_processing_time_ms\": %d,\n" +
                        "    \"total_requests\": %d,\n" +
                        "    \"timestamp\": %d,\n" +
                        "    \"warning\": \"此接口为DDoS攻击模拟目标，高频访问将导致服务器CPU资源耗尽。\"\n" +
                        "  }\n" +
                        "}",
                requestId, EXPENSIVE_CALCULATION_N, result, costTime, costTime, totalRequestCount.get(), System.currentTimeMillis()
        );

        log.info("【DDoS攻击目标】请求 #{} 处理完成 - 耗时: {} ms, 总请求数: {}, 结果: {}", 
                 requestId, costTime, totalRequestCount.get(), result);
        return response;
    }

    /**
     * 接口2：模拟I/O等待型接口（易受慢速攻击）
     * 攻击场景：攻击者建立大量慢速连接，占用服务器连接池资源。
     */
    @GetMapping("/io-delay")
    public String simulateIoDelay(@RequestParam(value = "delay", defaultValue = "1000") int delayMs) {
        long startTime = System.currentTimeMillis();
        long requestId = totalRequestCount.incrementAndGet();
        log.warn("【DDoS攻击目标-I/O延迟型】收到请求 #{}，将模拟 {} ms 延迟", requestId, delayMs);

        try {
            // 模拟I/O等待、数据库查询等耗时操作
            Thread.sleep(Math.min(delayMs, 10000)); // 限制最大延迟，避免过载
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【DDoS攻击目标】请求 #{} 被中断", requestId, e);
        }

        long costTime = System.currentTimeMillis() - startTime;
        
        String response = String.format(
                "{\n" +
                        "  \"code\": 200,\n" +
                        "  \"msg\": \"I/O操作模拟完成\",\n" +
                        "  \"data\": {\n" +
                        "    \"request_id\": %d,\n" +
                        "    \"simulated_delay_ms\": %d,\n" +
                        "    \"actual_cost_time_ms\": %d,\n" +
                        "    \"server_processing_time_ms\": %d,\n" +
                        "    \"total_requests\": %d,\n" +
                        "    \"timestamp\": %d,\n" +
                        "    \"warning\": \"此接口模拟I/O等待，慢速攻击（Slowloris）可长期占用此连接。\"\n" +
                        "  }\n" +
                        "}",
                requestId, delayMs, costTime, costTime, totalRequestCount.get(), System.currentTimeMillis()
        );
        
        log.info("【DDoS攻击目标】请求 #{} 处理完成 - 延迟: {} ms, 实际耗时: {} ms, 总请求数: {}", 
                 requestId, delayMs, costTime, totalRequestCount.get());
        return response;
    }

    /**
     * 接口3：简单的ping接口（易受洪水攻击）
     * 攻击场景：攻击者以极高频率调用最简单的接口，冲击服务器网络栈和请求处理队列。
     */
    @GetMapping("/ping")
    public String simplePing() {
        long startTime = System.currentTimeMillis();
        long requestId = totalRequestCount.incrementAndGet();
        
        // 对于高频Ping攻击，降低日志级别避免刷屏
        if (requestId % 100 == 1) {  // 每100个请求记录一次
            log.info("【DDoS攻击目标-Ping】收到请求 #{}, 总请求数: {}", requestId, totalRequestCount.get());
        }

        long costTime = System.currentTimeMillis() - startTime;
        
        return String.format(
                "{\n" +
                        "  \"code\": 200,\n" +
                        "  \"msg\": \"pong\",\n" +
                        "  \"data\": {\n" +
                        "    \"request_id\": %d,\n" +
                        "    \"timestamp\": %d,\n" +
                        "    \"server_response_time_ms\": %d,\n" +
                        "    \"total_requests\": %d\n" +
                        "  }\n" +
                        "}",
                requestId, System.currentTimeMillis(), costTime, totalRequestCount.get()
        );
    }

    /**
     * 接口4：获取当前被攻击状态
     */
    @GetMapping("/status")
    public String getAttackStatus() {
        long currentTotalRequests = totalRequestCount.get();
        log.info("【DDoS攻击目标】状态查询 - 当前总请求数: {}", currentTotalRequests);
        
        return String.format(
                "{\n" +
                        "  \"code\": 200,\n" +
                        "  \"msg\": \"DDoS被攻击目标状态\",\n" +
                        "  \"data\": {\n" +
                        "    \"total_requests_received\": %d,\n" +
                        "    \"status_check_timestamp\": %d,\n" +
                        "    \"available_targets\": [\n" +
                        "      {\"method\": \"GET\", \"path\": \"/target/ddos/compute-heavy\", \"desc\": \"CPU消耗型\", \"vulnerability\": \"易受洪水攻击，耗尽CPU资源\"},\n" +
                        "      {\"method\": \"GET\", \"path\": \"/target/ddos/io-delay?delay=5000\", \"desc\": \"I/O延迟型\", \"vulnerability\": \"易受慢速攻击，占用连接资源\"},\n" +
                        "      {\"method\": \"GET\", \"path\": \"/target/ddos/ping\", \"desc\": \"洪水攻击型\", \"vulnerability\": \"易受高频洪水攻击，冲击网络栈\"}\n" +
                        "    ],\n" +
                        "    \"security_warning\": \"⚠️ 此为安全测试环境，请勿在生产环境中使用！\",\n" +
                        "    \"tip\": \"攻击者（如压力测试工具）应向上述接口发起高频请求，以模拟DDoS攻击流量。\"\n" +
                        "  }\n" +
                        "}",
                currentTotalRequests, System.currentTimeMillis()
        );
    }

    // 模拟CPU密集型计算的辅助方法（斐波那契数列）
    private long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}