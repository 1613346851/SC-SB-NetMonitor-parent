package com.network.monitor.service.impl;

import com.network.monitor.entity.MenuEntity;
import com.network.monitor.entity.UserEntity;
import com.network.monitor.mapper.MenuMapper;
import com.network.monitor.mapper.RoleMenuMapper;
import com.network.monitor.mapper.UserMapper;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUsername = new ThreadLocal<>();
    
    private static final int STATUS_DISABLED = 1;
    private static final int STATUS_LOCKED = 2;
    
    @Value("${auth.login.max-fail-count:5}")
    private int maxFailCount;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private MenuMapper menuMapper;
    
    @Autowired
    private RoleMenuMapper roleMenuMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private OperLogService operLogService;

    @Override
    public UserEntity authenticate(String username, String password, String clientIp) {
        UserEntity user = userMapper.selectByUsername(username);
        
        if (user == null) {
            log.warn("登录失败：用户不存在，username={}", username);
            operLogService.logLogin(username, clientIp, 1, "用户不存在");
            return null;
        }
        
        if (user.getStatus() == STATUS_DISABLED) {
            log.warn("登录失败：账号已禁用，username={}", username);
            operLogService.logLogin(username, clientIp, 1, "账号已禁用");
            return null;
        }
        
        if (user.getStatus() == STATUS_LOCKED) {
            log.warn("登录失败：账号已锁定，username={}", username);
            operLogService.logLogin(username, clientIp, 1, "账号已锁定");
            return null;
        }
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            int failCount = user.getLoginFailCount() + 1;
            userMapper.updateLoginFailCount(user.getId(), failCount);
            
            if (failCount >= maxFailCount) {
                userMapper.updateStatus(user.getId(), STATUS_LOCKED);
                log.warn("账号已锁定：连续登录失败{}次，username={}", failCount, username);
                operLogService.logLogin(username, clientIp, 1, "账号已锁定，连续登录失败" + failCount + "次");
            } else {
                log.warn("登录失败：密码错误({}/{}), username={}", failCount, maxFailCount, username);
                operLogService.logLogin(username, clientIp, 1, "密码错误");
            }
            return null;
        }
        
        userMapper.resetLoginFailCount(user.getId());
        userMapper.updateLoginInfo(user.getId(), java.time.LocalDateTime.now(), clientIp);
        
        log.info("用户登录成功：username={}, ip={}", username, clientIp);
        operLogService.logLogin(username, clientIp, 0, null);
        
        return user;
    }

    @Override
    public UserEntity getCurrentUser() {
        Long userId = currentUserId.get();
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    @Override
    public Long getCurrentUserId() {
        return currentUserId.get();
    }

    @Override
    public String getCurrentUsername() {
        return currentUsername.get();
    }
    
    @Override
    public List<String> getCurrentUserPermissions() {
        Long userId = currentUserId.get();
        if (userId == null) {
            return Collections.emptyList();
        }
        return roleMenuMapper.selectPermissionsByUserId(userId);
    }
    
    @Override
    public List<MenuEntity> getCurrentUserMenus() {
        Long userId = currentUserId.get();
        if (userId == null) {
            return Collections.emptyList();
        }
        return menuMapper.selectByUserId(userId);
    }
    
    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        
        Long userId = currentUserId.get();
        if (userId == null) {
            return false;
        }
        
        List<String> permissions = roleMenuMapper.selectPermissionsByUserId(userId);
        return permissions.contains(permission) || permissions.contains("*:*:*");
    }
    
    @Override
    public void logout(String clientIp) {
        String username = currentUsername.get();
        if (username != null) {
            operLogService.logLogout(username, clientIp);
            log.info("用户退出登录：username={}, ip={}", username, clientIp);
        }
    }
    
    @Override
    public boolean changePassword(String currentPassword, String newPassword, String clientIp) {
        Long userId = currentUserId.get();
        String username = currentUsername.get();
        
        if (userId == null) {
            log.warn("修改密码失败：用户未登录");
            return false;
        }
        
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("修改密码失败：用户不存在，userId={}", userId);
            return false;
        }
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("修改密码失败：当前密码错误，username={}", username);
            operLogService.logOperation(username, "UPDATE", "个人中心", 
                "修改密码失败：当前密码错误", "changePassword", "/api/auth/changePassword", clientIp, 1);
            return false;
        }
        
        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(userId, encodedPassword, java.time.LocalDateTime.now());
        
        log.info("用户修改密码成功：username={}, ip={}", username, clientIp);
        operLogService.logOperation(username, "UPDATE", "个人中心", 
            "修改密码成功", "changePassword", "/api/auth/changePassword", clientIp, 0);
        
        return true;
    }
    
    public static void setCurrentUser(Long userId, String username) {
        currentUserId.set(userId);
        currentUsername.set(username);
    }
    
    public static void clearCurrentUser() {
        currentUserId.remove();
        currentUsername.remove();
    }
}
