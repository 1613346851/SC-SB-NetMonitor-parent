package com.network.monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步和定时任务配置类
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {
    // 线程池配置可通过配置文件自定义
}
