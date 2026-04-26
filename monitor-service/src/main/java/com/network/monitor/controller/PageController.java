package com.network.monitor.controller;

import com.network.monitor.common.exception.AuthRequiredException;
import com.network.monitor.entity.MenuEntity;
import com.network.monitor.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    @Autowired
    private AuthService authService;
    
    private static final Map<String, String> PAGE_PERMISSION_MAP = new HashMap<>();
    
    static {
        PAGE_PERMISSION_MAP.put("/", "dashboard");
        PAGE_PERMISSION_MAP.put("/traffic", "traffic");
        PAGE_PERMISSION_MAP.put("/event", "event");
        PAGE_PERMISSION_MAP.put("/attack", "attack");
        PAGE_PERMISSION_MAP.put("/vulnerability", "vulnerability");
        PAGE_PERMISSION_MAP.put("/scan", "scan");
        PAGE_PERMISSION_MAP.put("/defense", "defense");
        PAGE_PERMISSION_MAP.put("/defense-evaluation", "defense-evaluation");
        PAGE_PERMISSION_MAP.put("/rule", "rule");
        PAGE_PERMISSION_MAP.put("/blacklist", "blacklist");
        PAGE_PERMISSION_MAP.put("/alert", "alert");
        PAGE_PERMISSION_MAP.put("/trace", "trace");
        PAGE_PERMISSION_MAP.put("/report", "report");
        PAGE_PERMISSION_MAP.put("/config", "config");
        PAGE_PERMISSION_MAP.put("/system/user", "user");
        PAGE_PERMISSION_MAP.put("/system/role", "role");
        PAGE_PERMISSION_MAP.put("/system/log", "operlog");
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard() {
        checkPermission("/");
        return "dashboard";
    }

    @GetMapping("/traffic")
    public String trafficList() {
        checkPermission("/traffic");
        return "traffic-list";
    }

    @GetMapping("/attack")
    public String attackMonitor() {
        checkPermission("/attack");
        return "attack-monitor";
    }

    @GetMapping("/event")
    public String attackEvent() {
        checkPermission("/event");
        return "attack-event";
    }

    @GetMapping("/vulnerability")
    public String vulnerabilityMonitor() {
        checkPermission("/vulnerability");
        return "vulnerability-monitor";
    }

    @GetMapping("/scan")
    public String vulnerabilityScan() {
        checkPermission("/scan");
        return "vuln-scan";
    }

    @GetMapping("/defense")
    public String defenseLog() {
        checkPermission("/defense");
        return "defense-log";
    }

    @GetMapping("/defense-evaluation")
    public String defenseEvaluation() {
        checkPermission("/defense-evaluation");
        return "defense-evaluation";
    }

    @GetMapping("/rule")
    public String ruleManage() {
        checkPermission("/rule");
        return "rule-manage";
    }

    @GetMapping("/report")
    public String dataReport() {
        checkPermission("/report");
        return "data-report";
    }

    @GetMapping("/blacklist")
    public String blacklistManage() {
        checkPermission("/blacklist");
        return "blacklist-manage";
    }

    @GetMapping("/alert")
    public String alertManage() {
        checkPermission("/alert");
        return "alert-manage";
    }

    @GetMapping("/trace")
    public String traceSearch() {
        checkPermission("/trace");
        return "trace-search";
    }

    @GetMapping("/ip-profile")
    public String ipProfile() {
        return "ip-profile";
    }

    @GetMapping("/config")
    public String sysConfig() {
        checkPermission("/config");
        return "sys-config";
    }
    
    @GetMapping("/system/user")
    public String userManage() {
        checkPermission("/system/user");
        return "user-manage";
    }
    
    @GetMapping("/system/role")
    public String roleManage() {
        checkPermission("/system/role");
        return "role-manage";
    }
    
    @GetMapping("/system/menu")
    public String menuManage() {
        return "menu-manage";
    }
    
    @GetMapping("/system/log")
    public String operLog() {
        checkPermission("/system/log");
        return "oper-log";
    }
    
    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }
    
    @GetMapping("/forbidden")
    public String forbidden() {
        return "forbidden";
    }
    
    private void checkPermission(String path) {
        String requiredPermission = PAGE_PERMISSION_MAP.get(path);
        if (requiredPermission == null) {
            return;
        }
        
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AuthRequiredException("请先登录");
        }
        
        if (authService.isSuperAdmin()) {
            return;
        }
        
        List<MenuEntity> userMenus = authService.getCurrentUserMenus();
        if (userMenus == null || userMenus.isEmpty()) {
            throw new SecurityException("无访问权限");
        }
        
        boolean hasPermission = userMenus.stream()
                .anyMatch(menu -> requiredPermission.equals(menu.getPermission()));
        
        if (!hasPermission) {
            throw new SecurityException("无访问权限");
        }
    }
}
