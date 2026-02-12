package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL注入漏洞靶场（基于真实MySQL数据库）
 * 核心：直接拼接用户输入到SQL，用真实JDBC执行，完全还原生产环境漏洞
 */
@RestController
@RequestMapping("/target/sql")
@Slf4j
public class SqlVulnController {

    // 注入Spring JDBC（简化数据库操作，也可直接用原生JDBC）
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 数据库连接参数（也可从application.yml读取，这里简化）
    private static final String DB_URL = "jdbc:mysql://localhost:3306/vuln_target?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PWD = "123456";

    /**
     * 漏洞接口：直接拼接用户输入，用Statement执行（无参数化查询）
     */
    @GetMapping("/query")
    public String queryUser(@RequestParam("id") String userId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            // 漏洞核心：无过滤拼接用户输入到SQL（真实生产环境漏洞成因）
            String sql = "SELECT id, username, password, phone FROM sys_user WHERE id = " + userId;
            log.warn("【高危SQL注入漏洞】执行未过滤的SQL：{}", sql);

            // 1. 真实JDBC连接（不使用PreparedStatement，保留漏洞）
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);
            stmt = conn.createStatement(); // 关键：用Statement（有注入），而非PreparedStatement（安全）

            // 2. 执行SQL（真实数据库执行，而非内存模拟）
            rs = stmt.executeQuery(sql);

            // 3. 解析查询结果（真实数据库返回的结果集）
            List<Map<String, String>> queryResult = parseResultSet(rs);

            return buildSuccessResponse(queryResult, sql);

        } catch (Exception e) {
            log.error("【SQL执行异常】注入攻击触发真实数据库报错：", e);
            return buildErrorResponse(e.getMessage());
        } finally {
            // 关闭资源（避免连接泄漏）
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {
                log.error("关闭数据库资源失败", e);
            }
        }
    }

    /**
     * 安全对比接口：使用PreparedStatement参数化查询
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
     * 解析真实数据库的ResultSet结果集
     */
    private List<Map<String, String>> parseResultSet(ResultSet rs) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        while (rs.next()) {
            Map<String, String> user = new HashMap<>();
            user.put("id", rs.getString("id"));
            user.put("username", rs.getString("username"));
            user.put("password", rs.getString("password"));
            user.put("phone", rs.getString("phone"));
            result.add(user);
        }
        return result;
    }

    /**
     * 构造成功响应（复用原有逻辑）
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
     * 构造错误响应（复用原有逻辑）
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