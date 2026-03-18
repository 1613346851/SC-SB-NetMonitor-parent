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
@RequestMapping({"/page", "/target/page"})
@Slf4j
public class PageController {

    /**
     * 首页路由 - 漏洞类型选择页面
     */
    @GetMapping({"/index", "/"})
    public String indexPage(Model model) {
        log.info("访问靶场首页");
        model.addAttribute("pageTitle", "安全测试靶场");
        return "index";
    }

    /**
     * CMD命令注入漏洞测试页面
     */
    @GetMapping("/cmd-vuln")
    public String cmdVulnPage(Model model) {
        log.info("访问CMD命令注入漏洞测试页面");
        model.addAttribute("pageTitle", "CMD命令注入漏洞测试平台");
        model.addAttribute("pageDescription", "模拟生产环境的命令执行系统，支持Windows/Linux命令测试");
        model.addAttribute("apiEndpoint", "/target/cmd/execute");
        return "cmd-vuln";
    }

    /**
     * SQL注入漏洞测试页面
     */
    @GetMapping("/sql-vuln")
    public String sqlVulnPage(Model model) {
        log.info("访问SQL注入漏洞测试页面");
        model.addAttribute("pageTitle", "SQL注入漏洞测试平台");
        model.addAttribute("pageDescription", "模拟生产环境的数据库查询系统，支持SQL注入测试");
        model.addAttribute("vulnApiEndpoint", "/target/sql/query");
        model.addAttribute("safeApiEndpoint", "/target/sql/safe-query");
        return "sql-vuln";
    }

    /**
     * XSS漏洞测试页面
     */
    @GetMapping("/xss-vuln")
    public String xssVulnPage(Model model) {
        log.info("访问XSS漏洞测试页面");
        model.addAttribute("pageTitle", "XSS漏洞测试平台");
        model.addAttribute("pageDescription", "支持存储型、反射型、DOM型三种XSS漏洞测试");
        return "xss-vuln";
    }

    /**
     * DDoS攻击模拟测试页面
     */
    @GetMapping("/ddos-vuln")
    public String ddosTargetPage(Model model) {
        log.info("访问DDoS攻击模拟测试页面");
        model.addAttribute("pageTitle", "DDoS攻击模拟靶场");
        model.addAttribute("pageDescription", "模拟DDoS攻击测试环境，支持多种攻击场景验证");
        return "ddos-vuln";
    }

    /**
     * 路径遍历漏洞测试页面
     */
    @GetMapping("/path-traversal-vuln")
    public String pathTraversalVulnPage(Model model) {
        log.info("访问路径遍历漏洞测试页面");
        model.addAttribute("pageTitle", "路径遍历漏洞测试平台");
        model.addAttribute("pageDescription", "模拟文件读取功能，支持路径遍历攻击测试");
        return "path-traversal-vuln";
    }

    /**
     * 文件包含漏洞测试页面
     */
    @GetMapping("/file-include-vuln")
    public String fileIncludeVulnPage(Model model) {
        log.info("访问文件包含漏洞测试页面");
        model.addAttribute("pageTitle", "文件包含漏洞测试平台");
        model.addAttribute("pageDescription", "模拟动态加载配置文件功能，支持文件包含攻击测试");
        return "file-include-vuln";
    }

    /**
     * SSRF服务端请求伪造漏洞测试页面
     */
    @GetMapping("/ssrf-vuln")
    public String ssrfVulnPage(Model model) {
        log.info("访问SSRF漏洞测试页面");
        model.addAttribute("pageTitle", "SSRF服务端请求伪造漏洞测试平台");
        model.addAttribute("pageDescription", "模拟URL链接预览功能，支持SSRF攻击测试");
        return "ssrf-vuln";
    }

    /**
     * XXE XML外部实体注入漏洞测试页面
     */
    @GetMapping("/xxe-vuln")
    public String xxeVulnPage(Model model) {
        log.info("访问XXE漏洞测试页面");
        model.addAttribute("pageTitle", "XXE XML外部实体注入漏洞测试平台");
        model.addAttribute("pageDescription", "模拟XML数据解析功能，支持XXE攻击测试");
        return "xxe-vuln";
    }

    /**
     * Java反序列化漏洞测试页面
     */
    @GetMapping("/deserial-vuln")
    public String deserialVulnPage(Model model) {
        log.info("访问Java反序列化漏洞测试页面");
        model.addAttribute("pageTitle", "Java反序列化漏洞测试平台");
        model.addAttribute("pageDescription", "模拟对象序列化/反序列化功能，支持反序列化攻击测试");
        return "deserial-vuln";
    }

    /**
     * CSRF跨站请求伪造漏洞测试页面
     */
    @GetMapping("/csrf-vuln")
    public String csrfVulnPage(Model model) {
        log.info("访问CSRF漏洞测试页面");
        model.addAttribute("pageTitle", "CSRF跨站请求伪造漏洞测试平台");
        model.addAttribute("pageDescription", "模拟用户信息修改功能，支持CSRF攻击测试");
        return "csrf-vuln";
    }
}
