package com.network.monitor.config;

import com.network.monitor.entity.UserEntity;
import com.network.monitor.mapper.UserMapper;
import com.network.monitor.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AdminUserInitializer implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final Long SUPER_ADMIN_ROLE_ID = 1L;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserRoleMapper userRoleMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Value("${admin.init.password:}")
    private String configuredPassword;
    
    private final SecureRandom random = new SecureRandom();
    
    @Override
    public void run(String... args) throws Exception {
        String defaultUsername = "admin";
        
        UserEntity existingUser = userMapper.selectByUsername(defaultUsername);
        
        if (existingUser != null) {
            log.info("管理员账号已存在，跳过初始化: username={}", defaultUsername);
            return;
        }
        
        String initialPassword;
        boolean useConfiguredPassword = configuredPassword != null && !configuredPassword.trim().isEmpty();
        
        if (useConfiguredPassword) {
            initialPassword = configuredPassword.trim();
            log.info("使用配置文件中的初始密码创建管理员账号");
        } else {
            initialPassword = generateSecurePassword(12);
            log.warn("========================================");
            log.warn("【重要】首次启动，已自动生成管理员密码");
            log.warn("用户名: {}", defaultUsername);
            log.warn("密码: {}", initialPassword);
            log.warn("请立即登录并修改密码！");
            log.warn("========================================");
        }
        
        UserEntity newUser = new UserEntity();
        newUser.setUsername(defaultUsername);
        newUser.setPassword(passwordEncoder.encode(initialPassword));
        newUser.setNickname("系统管理员");
        newUser.setEmail("admin@example.com");
        newUser.setStatus(0);
        newUser.setLoginFailCount(0);
        newUser.setDelFlag(0);
        newUser.setCreateBy("system");
        newUser.setRemark(useConfiguredPassword ? "配置文件初始化的管理员账号" : "自动生成密码的管理员账号");
        
        userMapper.insert(newUser);
        
        userRoleMapper.insert(newUser.getId(), SUPER_ADMIN_ROLE_ID);
        
        log.info("管理员账号创建成功: username={}, 已分配超级管理员角色", defaultUsername);
    }
    
    private String generateSecurePassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return password.toString();
    }
}
