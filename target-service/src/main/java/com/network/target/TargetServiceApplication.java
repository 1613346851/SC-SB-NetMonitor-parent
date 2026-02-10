package com.network.target;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 靶场服务启动类
 * 核心作用：初始化Spring容器，扫描漏洞接口组件
 */

@SpringBootApplication
public class TargetServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TargetServiceApplication.class, args);
		System.out.println("========================");
		System.out.println("靶场服务（target-service）启动成功！");
		System.out.println("端口：9001，接口前缀：/target/**");
		System.out.println("========================");
	}

}
