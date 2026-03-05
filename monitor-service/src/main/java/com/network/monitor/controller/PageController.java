package com.network.monitor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端页面路由渲染控制器
 */
@Controller
public class PageController {

    /**
     * 仪表盘页面
     */
    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 流量列表页面
     */
    @GetMapping("/traffic")
    public String trafficList() {
        return "traffic-list";
    }

    /**
     * 攻击监测页面
     */
    @GetMapping("/attack")
    public String attackMonitor() {
        return "attack-monitor";
    }

    /**
     * 漏洞监测页面
     */
    @GetMapping("/vulnerability")
    public String vulnerabilityMonitor() {
        return "vulnerability-monitor";
    }

    /**
     * 防御日志页面
     */
    @GetMapping("/defense")
    public String defenseLog() {
        return "defense-log";
    }

    /**
     * 规则管理页面
     */
    @GetMapping("/rule")
    public String ruleManage() {
        return "rule-manage";
    }

    /**
     * 数据报表页面
     */
    @GetMapping("/report")
    public String dataReport() {
        return "data-report";
    }

    /**
     * 黑名单管理页面
     */
    @GetMapping("/blacklist")
    public String blacklistManage() {
        return "blacklist-manage";
    }
}
