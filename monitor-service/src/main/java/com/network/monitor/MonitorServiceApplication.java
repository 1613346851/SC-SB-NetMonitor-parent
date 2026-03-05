package com.network.monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监测服务启动类
 */
@SpringBootApplication
@MapperScan("com.network.monitor.mapper")
@EnableAsync
@EnableScheduling
@EnableRetry
public class MonitorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitorServiceApplication.class, args);
        System.out.println("====================================");
        System.out.println("监测业务服务启动成功！");
        System.out.println("服务端口：9002");
        System.out.println("====================================");
    }

}
