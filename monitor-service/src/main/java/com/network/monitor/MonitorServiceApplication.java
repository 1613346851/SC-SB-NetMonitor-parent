package com.network.monitor;

import com.network.monitor.config.AdminUserInitializer;
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
        
        String adminPassword = AdminUserInitializer.getGeneratedPassword();
        if (adminPassword != null) {
            System.out.println();
            System.out.println("================================================================================");
            System.out.println("【重要提示】管理员账号初始化成功");
            System.out.println("================================================================================");
            System.out.println("用户名: admin");
            System.out.println("密码:   " + adminPassword);
            System.out.println("================================================================================");
            System.out.println("请立即使用上述凭据登录系统，并修改密码！");
            System.out.println("================================================================================");
            System.out.println();
        }
    }

}
