package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL注入漏洞靶场（基于真实MySQL数据库，使用Spring JdbcTemplate）
 */
@RestController
@RequestMapping("/target/sql")
@Slf4j
public class SqlVulnController {

    // 注入Spring自动配置的JdbcTemplate，它会读取application.yml中的数据库配置
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 漏洞接口：直接拼接用户输入，用JdbcTemplate执行（无参数化查询）
     */
    @GetMapping("/query")
    public String queryUser(@RequestParam("id") String userId) {
        try {
            // 漏洞核心：无过滤拼接用户输入到SQL
            String sql = "SELECT id, username, password, phone FROM sys_user WHERE id = " + userId;
            log.warn("【高危SQL注入漏洞】执行未过滤的SQL：{}", sql);

            // 使用Spring注入的JdbcTemplate执行SQL，自动读取application.yml的配置
            List<Map<String, Object>> queryResult = jdbcTemplate.queryForList(sql);

            // 转换结果格式
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, Object> row : queryResult) {
                Map<String, String> user = new HashMap<>();
                user.put("id", row.get("id").toString());
                user.put("username", row.get("username").toString());
                user.put("password", row.get("password").toString());
                user.put("phone", row.get("phone").toString());
                result.add(user);
            }

            return buildSuccessResponse(result, sql);

        } catch (Exception e) {
            log.error("【SQL执行异常】注入攻击触发真实数据库报错：", e);
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * 安全对比接口：使用参数化查询
     */
    @GetMapping("/safe-query")
    public String safeQuery(@RequestParam("id") String userId) {
        // 基础校验
        if (!userId.matches("\\d+")) {
            return buildErrorResponse("无效的用户ID（仅支持数字）");
        }

        try {
            // 安全核心：参数化查询（?占位符，避免注入）
            String sql = "SELECT id, username, password, phone FROM sys_user WHERE id = ?";
            log.info("【安全查询】执行参数化SQL：{}", sql);

            // 执行参数化查询
            List<Map<String, Object>> safeResult = jdbcTemplate.queryForList(sql, userId);

            // 转换结果格式
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, Object> row : safeResult) {
                Map<String, String> user = new HashMap<>();
                user.put("id", row.get("id").toString());
                user.put("username", row.get("username").toString());
                user.put("password", row.get("password").toString());
                user.put("phone", row.get("phone").toString());
                result.add(user);
            }

            return buildSuccessResponse(result, sql);
        } catch (Exception e) {
            log.error("安全查询异常", e);
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * 构造成功响应
     */
    private String buildSuccessResponse(List<Map<String, String>> data, String sql) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"code\": 200,\n");
        json.append("  \"msg\": \"用户查询成功\",\n");
        json.append("  \"data\": {\n");
        json.append("    \"executed_sql\": \"").append(sql.replace("\"", "\\\"")).append("\",\n");
        json.append("    \"user_list\": [");
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> user = data.get(i);
            json.append("\n      {\n");
            json.append("        \"id\": \"").append(user.get("id")).append("\",\n");
            json.append("        \"username\": \"").append(user.get("username")).append("\",\n");
            json.append("        \"password\": \"").append(user.get("password")).append("\",\n");
            json.append("        \"phone\": \"").append(user.get("phone")).append("\"\n");
            json.append("      }");
            if (i < data.size() - 1) json.append(",");
        }
        json.append("\n    ],\n");
        json.append("    \"warning\": \"此接口存在高危SQL注入漏洞（真实数据库执行）！\"\n");
        json.append("  }\n");
        json.append("}");
        return json.toString();
    }

    /**
     * 构造错误响应
     */
    private String buildErrorResponse(String errorMsg) {
        return "{\n" +
                "  \"code\": 500,\n" +
                "  \"msg\": \"SQL执行失败\",\n" +
                "  \"error\": \"" + errorMsg.replace("\"", "\\\"") + "\",\n" +
                "  \"warning\": \"注入攻击触发真实数据库异常！\"\n" +
                "}";
    }
}