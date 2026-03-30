package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.RoleEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/role")
public class RoleController {

    @Autowired
    private RoleService roleService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 10;
        
        int offset = (pageNum - 1) * pageSize;
        
        List<RoleEntity> roles = roleService.listRoles(roleName, status, offset, pageSize);
        long total = roleService.countRoles(roleName, status);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", roles);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        
        return ApiResponse.success(result);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        RoleEntity role = roleService.getById(id);
        if (role == null) {
            return ApiResponse.notFound("角色不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("menuIds", roleService.getRoleMenuIds(id));
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String roleName = (String) params.get("roleName");
        String roleCode = (String) params.get("roleCode");
        String roleDesc = (String) params.get("roleDesc");
        String remark = (String) params.get("remark");
        List<Long> menuIds = extractLongList(params.get("menuIds"));
        
        if (roleName == null || roleName.trim().isEmpty()) {
            return ApiResponse.badRequest("角色名称不能为空");
        }
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return ApiResponse.badRequest("角色编码不能为空");
        }
        
        if (roleService.checkRoleCodeExists(roleCode)) {
            return ApiResponse.badRequest("角色编码已存在");
        }
        
        RoleEntity role = new RoleEntity();
        role.setRoleName(roleName.trim());
        role.setRoleCode(roleCode.trim().toUpperCase());
        role.setRoleDesc(roleDesc);
        role.setRemark(remark);
        role.setCreateBy(authService.getCurrentUsername());
        
        boolean success = roleService.createRole(role, menuIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "角色管理", 
                "新增角色：" + roleName, "add", "/api/system/role/add", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("创建角色失败");
    }
    
    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long id = Long.valueOf(params.get("id").toString());
        String roleName = (String) params.get("roleName");
        String roleDesc = (String) params.get("roleDesc");
        String remark = (String) params.get("remark");
        List<Long> menuIds = extractLongList(params.get("menuIds"));
        
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleName(roleName);
        role.setRoleDesc(roleDesc);
        role.setRemark(remark);
        role.setUpdateBy(authService.getCurrentUsername());
        
        boolean success = roleService.updateRole(role, menuIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "角色管理", 
                "更新角色ID：" + id, "update", "/api/system/role/update", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("更新角色失败");
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        boolean success = roleService.deleteRole(id);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "角色管理", 
                "删除角色ID：" + id, "delete", "/api/system/role/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("删除角色失败");
    }
    
    @PutMapping("/assignMenus/{roleId}")
    public ApiResponse<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds,
                                          HttpServletRequest request) {
        boolean success = roleService.assignMenus(roleId, menuIds);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "角色管理", 
                "分配权限，角色ID：" + roleId, "assignMenus", "/api/system/role/assignMenus/" + roleId, 
                getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("分配权限失败");
    }
    
    @SuppressWarnings("unchecked")
    private List<Long> extractLongList(Object obj) {
        if (obj == null) {
            return new ArrayList<>();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Long> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Number) {
                    result.add(((Number) item).longValue());
                } else if (item instanceof String) {
                    try {
                        result.add(Long.parseLong((String) item));
                    } catch (NumberFormatException e) {
                        // 忽略无效数字
                    }
                }
            }
            return result;
        }
        return new ArrayList<>();
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
}
