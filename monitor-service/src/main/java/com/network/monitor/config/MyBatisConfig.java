package com.network.monitor.config;

import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置类
 */
@Configuration
public class MyBatisConfig {
    // MyBatis 配置通过 application.yml 完成
    // Mapper 扫描在 MonitorServiceApplication 中通过 @MapperScan 注解配置
}
