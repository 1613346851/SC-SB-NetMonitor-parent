package com.network.monitor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/traffic")
    public String trafficList() {
        return "traffic-list";
    }

    @GetMapping("/attack")
    public String attackMonitor() {
        return "attack-monitor";
    }

    @GetMapping("/event")
    public String attackEvent() {
        return "attack-event";
    }

    @GetMapping("/vulnerability")
    public String vulnerabilityMonitor() {
        return "vulnerability-monitor";
    }

    @GetMapping("/scan")
    public String vulnerabilityScan() {
        return "vuln-scan";
    }

    @GetMapping("/defense")
    public String defenseLog() {
        return "defense-log";
    }

    @GetMapping("/defense-evaluation")
    public String defenseEvaluation() {
        return "defense-evaluation";
    }

    @GetMapping("/rule")
    public String ruleManage() {
        return "rule-manage";
    }

    @GetMapping("/report")
    public String dataReport() {
        return "data-report";
    }

    @GetMapping("/blacklist")
    public String blacklistManage() {
        return "blacklist-manage";
    }

    @GetMapping("/config")
    public String sysConfig() {
        return "sys-config";
    }
    
    @GetMapping("/system/user")
    public String userManage() {
        return "user-manage";
    }
    
    @GetMapping("/system/role")
    public String roleManage() {
        return "role-manage";
    }
    
    @GetMapping("/system/menu")
    public String menuManage() {
        return "menu-manage";
    }
    
    @GetMapping("/system/log")
    public String operLog() {
        return "oper-log";
    }
    
    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }
}
