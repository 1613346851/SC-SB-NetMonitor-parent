package com.network.monitor.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置类
 */
@Configuration
@MapperScan("com.network.monitor.mapper")
public class MyBatisConfig {
    // MyBatis 配置通过 application.yml 完成
}
