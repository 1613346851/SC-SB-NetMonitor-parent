package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.MenuEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.MenuService;
import com.network.monitor.service.OperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<List<MenuEntity>> list(@RequestParam(required = false) String menuName,
                                               @RequestParam(required = false) Integer status) {
        List<MenuEntity> menus = menuService.listMenus(menuName, status);
        return ApiResponse.success(menus);
    }
    
    @GetMapping("/all")
    public ApiResponse<List<MenuEntity>> getAll() {
        List<MenuEntity> menus = menuService.getAllMenus();
        return ApiResponse.success(menus);
    }
    
    @GetMapping("/tree")
    public ApiResponse<List<MenuEntity>> getTree() {
        List<MenuEntity> menus = menuService.getMenuTree();
        return ApiResponse.success(menus);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<MenuEntity> getById(@PathVariable Long id) {
        MenuEntity menu = menuService.getById(id);
        if (menu == null) {
            return ApiResponse.notFound("菜单不存在");
        }
        return ApiResponse.success(menu);
    }
    
    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody MenuEntity menu, HttpServletRequest request) {
        if (menu.getMenuName() == null || menu.getMenuName().trim().isEmpty()) {
            return ApiResponse.badRequest("菜单名称不能为空");
        }
        
        menu.setCreateBy(authService.getCurrentUsername());
        
        boolean success = menuService.createMenu(menu);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "菜单管理", 
                "新增菜单：" + menu.getMenuName(), "add", "/api/system/menu/add", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("创建菜单失败");
    }
    
    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody MenuEntity menu, HttpServletRequest request) {
        menu.setUpdateBy(authService.getCurrentUsername());
        
        boolean success = menuService.updateMenu(menu);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "菜单管理", 
                "更新菜单ID：" + menu.getId(), "update", "/api/system/menu/update", getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("更新菜单失败");
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (menuService.hasChildren(id)) {
            return ApiResponse.badRequest("存在子菜单，无法删除");
        }
        
        boolean success = menuService.deleteMenu(id);
        if (success) {
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "菜单管理", 
                "删除菜单ID：" + id, "delete", "/api/system/menu/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        }
        return ApiResponse.error("删除菜单失败");
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
