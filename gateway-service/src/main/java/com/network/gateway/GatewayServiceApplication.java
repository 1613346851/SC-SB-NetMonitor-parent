package com.network.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关服务启动类
 * 基于Spring Cloud Gateway 2021.0.8开发的响应式网关服务
 * 核心功能：流量采集、基础防御、跨服务交互
 *
 * @author network-monitor
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class GatewayServiceApplication {

	public static void main(String[] args) {
		// 启动网关服务
		SpringApplication.run(GatewayServiceApplication.class, args);
		System.out.println("===============================================");
		System.out.println("网络安全监测系统 - 网关服务启动成功！");
		System.out.println("服务端口: 9000");
		System.out.println("路由前缀: /target/** -> 靶场服务(9001)");
		System.out.println("管理端点: /actuator/**");
		System.out.println("防御接口: /api/gateway/defense/**");
		System.out.println("===============================================");
	}
}
