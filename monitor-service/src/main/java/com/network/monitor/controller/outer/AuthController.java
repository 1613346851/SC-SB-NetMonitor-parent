package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.MenuEntity;
import com.network.monitor.entity.UserEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private OperLogService operLogService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> loginForm,
                                                   HttpServletRequest request) {
        String username = loginForm.get("username");
        String password = loginForm.get("password");
        
        if (username == null || username.trim().isEmpty()) {
            return ApiResponse.badRequest("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return ApiResponse.badRequest("密码不能为空");
        }
        
        String clientIp = getClientIp(request);
        UserEntity user = authService.authenticate(username, password, clientIp);
        
        if (user == null) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        
        if (user.getStatus() != null && user.getStatus() == 1) {
            return ApiResponse.forbidden("账号已被禁用，请联系管理员");
        }
        
        if (user.getStatus() != null && user.getStatus() == 2) {
            return ApiResponse.forbidden("账号已被锁定，请联系管理员解锁");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserInfo(user));
        
        return ApiResponse.success(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        authService.logout(clientIp);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser() {
        UserEntity user = authService.getCurrentUser();
        if (user == null) {
            return ApiResponse.unauthorized("未登录");
        }
        return ApiResponse.success(buildUserInfo(user));
    }
    
    @GetMapping("/menus")
    public ApiResponse<List<MenuEntity>> getCurrentUserMenus() {
        List<MenuEntity> menus = authService.getCurrentUserMenus();
        return ApiResponse.success(menus);
    }
    
    @GetMapping("/permissions")
    public ApiResponse<List<String>> getCurrentUserPermissions() {
        List<String> permissions = authService.getCurrentUserPermissions();
        return ApiResponse.success(permissions);
    }
    
    @PostMapping("/changePassword")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> passwordForm,
                                             HttpServletRequest request) {
        String currentPassword = passwordForm.get("currentPassword");
        String newPassword = passwordForm.get("newPassword");
        
        if (currentPassword == null || currentPassword.isEmpty()) {
            return ApiResponse.badRequest("当前密码不能为空");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return ApiResponse.badRequest("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            return ApiResponse.badRequest("新密码长度不能少于6位");
        }
        
        String clientIp = getClientIp(request);
        boolean success = authService.changePassword(currentPassword, newPassword, clientIp);
        
        if (success) {
            return ApiResponse.success();
        }
        return ApiResponse.error("当前密码错误");
    }

    private Map<String, Object> buildUserInfo(UserEntity user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("status", user.getStatus());
        return userInfo;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
