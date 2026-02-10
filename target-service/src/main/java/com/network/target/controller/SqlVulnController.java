package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL注入漏洞接口（预设漏洞）
 * 接口路径：/target/sql/**，与网关路由的/target/**前缀匹配
 */
@RestController
@RequestMapping("/target/sql")
@Slf4j
public class SqlVulnController {

    /**
     * 模拟“用户查询”接口（存在SQL注入漏洞）
     * 漏洞点：直接拼接用户输入的id参数到SQL，未做过滤
     * 测试攻击请求：http://localhost:9001/target/sql/query?id=1 union select 1,2,3
     */
    @GetMapping("/query")
    public String queryUser(@RequestParam("id") String userId) {
        // 1. 模拟SQL拼接（漏洞核心：未过滤用户输入）
        String sql = "SELECT id, username, password FROM sys_user WHERE id = " + userId;

        // 2. 日志记录请求（便于后续排查流量）
        log.info("【SQL漏洞接口】执行模拟SQL：{}", sql);

        // 3. 模拟返回结果（不实际操作数据库，仅返回SQL语句用于验证）
        return "{\n" +
                "  \"code\": 200,\n" +
                "  \"msg\": \"SQL查询成功（模拟）\",\n" +
                "  \"data\": {\n" +
                "    \"executed_sql\": \"" + sql + "\",\n" +
                "    \"warning\": \"此接口存在SQL注入漏洞，未过滤用户输入\"\n" +
                "  }\n" +
                "}";
    }

}