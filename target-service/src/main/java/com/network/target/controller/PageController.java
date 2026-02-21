package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 页面路由控制器
 * 负责处理前端页面访问请求，提供统一的页面入口
 */
@Controller
@RequestMapping("/page")
@Slf4j
public class PageController {

    /**
     * CMD命令注入漏洞测试页面
     * 提供模拟生产环境的命令执行界面
     */
    @GetMapping("/cmd-vuln")
    public String cmdVulnPage(Model model) {
        log.info("访问CMD命令注入漏洞测试页面");
        
        // 添加页面元数据
        model.addAttribute("pageTitle", "CMD命令注入漏洞测试平台");
        model.addAttribute("pageDescription", "模拟生产环境的命令执行系统，支持Windows/Linux命令测试");
        model.addAttribute("apiEndpoint", "/target/cmd/execute");
        
        return "cmd-vuln";
    }

    /**
     * SQL注入漏洞测试页面
     * 提供模拟生产环境的数据库查询界面
     */
    @GetMapping("/sql-vuln")
    public String sqlVulnPage(Model model) {
        log.info("访问SQL注入漏洞测试页面");
        
        // 添加页面元数据
        model.addAttribute("pageTitle", "SQL注入漏洞测试平台");
        model.addAttribute("pageDescription", "模拟生产环境的数据库查询系统，支持SQL注入测试");
        model.addAttribute("vulnApiEndpoint", "/target/sql/query");
        model.addAttribute("safeApiEndpoint", "/target/sql/safe-query");
        
        return "sql-vuln";
    }

    /**
     * 首页路由 - 可扩展为漏洞类型选择页面
     */
    @GetMapping("/index")
    public String indexPage(Model model) {
        log.info("访问靶场首页");
        model.addAttribute("pageTitle", "安全测试靶场");
        return "index";
    }
}