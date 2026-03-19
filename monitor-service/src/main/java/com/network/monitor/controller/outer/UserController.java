package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.UserEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/user")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<List<UserEntity>> list(@RequestParam(required = false) String username,
                                               @RequestParam(required = false) Integer status) {
        List<UserEntity> users = userService.listUsers(username, status);
        return ApiResponse.success(users);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        UserEntity user = userService.getById(id);
        if (user == null) {
            return ApiResponse.notFound("用户不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("roleIds", userService.getUserRoleIds(id));
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String nickname = (String) params.get("nickname");
        String phone = (String) params.get("phone");
        String email = (String) params.get("email");
        String remark = (String) params.get("remark");
        List<Long> roleIds = convertToLongList(params.get("roleIds"));
        
        if (username == null || username.trim().isEmpty()) {
            return ApiResponse.badRequest("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return ApiResponse.badRequest("密码不能为空");
        }
        
        if (userService.checkUsernameExists(username)) {
            return ApiResponse.badRequest("用户名已存在");
        }
        
        UserEntity user = new UserEntity();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRemark(remark);
        user.setCreateBy(authService.getCurrentUsername());
        
        boolean success = userService.createUser(user, roleIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "用户管理", 
                "新增用户：" + username, "add", "/api/system/user/add", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("创建用户失败");
    }
    
    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long id = Long.valueOf(params.get("id").toString());
        String nickname = (String) params.get("nickname");
        String phone = (String) params.get("phone");
        String email = (String) params.get("email");
        String remark = (String) params.get("remark");
        List<Long> roleIds = convertToLongList(params.get("roleIds"));
        
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRemark(remark);
        user.setUpdateBy(authService.getCurrentUsername());
        
        boolean success = userService.updateUser(user, roleIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "用户管理", 
                "更新用户ID：" + id, "update", "/api/system/user/update", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("更新用户失败");
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        boolean success = userService.deleteUser(id);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "用户管理", 
                "删除用户ID：" + id, "delete", "/api/system/user/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("删除用户失败");
    }
    
    @PutMapping("/status/{id}")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status,
                                           HttpServletRequest request) {
        boolean success = userService.updateStatus(id, status);
        if (success) {
            String action = status == 0 ? "启用" : (status == 1 ? "禁用" : "解锁");
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "用户管理", 
                action + "用户ID：" + id, "updateStatus", "/api/system/user/status/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("操作失败");
    }
    
    @PutMapping("/resetPwd/{id}")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestParam String newPassword,
                                            HttpServletRequest request) {
        boolean success = userService.resetPassword(id, newPassword);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "用户管理", 
                "重置用户密码ID：" + id, "resetPassword", "/api/system/user/resetPwd/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("重置密码失败");
    }
    
    @PutMapping("/assignRoles/{userId}")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds,
                                          HttpServletRequest request) {
        boolean success = userService.assignRoles(userId, roleIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "用户管理", 
                "分配角色，用户ID：" + userId, "assignRoles", "/api/system/user/assignRoles/" + userId, 
                getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("分配角色失败");
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    @SuppressWarnings("unchecked")
    private List<Long> convertToLongList(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return list.stream()
                    .map(item -> {
                        if (item instanceof Number) {
                            return ((Number) item).longValue();
                        }
                        return Long.valueOf(item.toString());
                    })
                    .collect(java.util.stream.Collectors.toList());
        }
        return null;
    }
}
