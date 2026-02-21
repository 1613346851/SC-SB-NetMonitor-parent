package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
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
            String sql = "SELECT id, username, password, phone, create_time FROM sys_user WHERE id = " + userId;
            log.warn("【高危SQL注入漏洞】执行未过滤的SQL：{}", sql);

            // 检查是否包含多条SQL语句（堆叠查询）
            if (userId.contains(";")) {
                return executeMultipleStatements(sql, userId);
            }

            // 使用Spring注入的JdbcTemplate执行SQL，自动读取application.yml的配置
            List<Map<String, Object>> queryResult = jdbcTemplate.queryForList(sql);

            // 转换结果格式 - 增强null值处理
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, Object> row : queryResult) {
                Map<String, String> user = new HashMap<>();
                user.put("id", row.get("id") != null ? row.get("id").toString() : "");
                user.put("username", row.get("username") != null ? row.get("username").toString() : "");
                user.put("password", row.get("password") != null ? row.get("password").toString() : "");
                user.put("phone", row.get("phone") != null ? row.get("phone").toString() : "");
                // 处理create_time字段，可能为null
                Object createTimeObj = row.get("create_time");
                String createTimeStr = createTimeObj != null ? createTimeObj.toString() : "";
                user.put("create_time", createTimeStr);
                result.add(user);
            }

            return buildSuccessResponse(result, sql);

        } catch (Exception e) {
            log.error("【SQL执行异常】注入攻击触发真实数据库报错：", e);
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * 执行多条SQL语句（堆叠查询支持）
     */
    private String executeMultipleStatements(String baseSql, String userId) {
        try {
            // 获取数据库连接
            Connection conn = jdbcTemplate.getDataSource().getConnection();
            Statement stmt = conn.createStatement();
            
            // 执行多条SQL语句
            boolean hasResultSet = stmt.execute(baseSql);
            
            List<List<Map<String, String>>> allResults = new ArrayList<>();
            
            // 处理第一个结果集
            if (hasResultSet) {
                ResultSet rs = stmt.getResultSet();
                allResults.add(resultSetToList(rs));
                rs.close();
            }
            
            // 处理后续结果集
            while (stmt.getMoreResults()) {
                ResultSet rs = stmt.getResultSet();
                allResults.add(resultSetToList(rs));
                rs.close();
            }
            
            stmt.close();
            conn.close();
            
            return buildMultiStatementResponse(allResults, baseSql);
            
        } catch (Exception e) {
            log.error("【多语句执行异常】", e);
            return buildErrorResponse(e.getMessage());
        }
    }
    
    /**
     * 将ResultSet转换为List<Map<String, String>>
     */
    private List<Map<String, String>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, String>> result = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        while (rs.next()) {
            Map<String, String> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                row.put(columnName, value != null ? value.toString() : "");
            }
            result.add(row);
        }
        
        return result;
    }
    
    /**
     * 构造多语句执行的响应
     */
    private String buildMultiStatementResponse(List<List<Map<String, String>>> allResults, String sql) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"code\": 200,\n");
        json.append("  \"msg\": \"多语句执行成功\",\n");
        json.append("  \"data\": {\n");
        json.append("    \"executed_sql\": \"").append(escapeJsonString(sql)).append("\",\n");
        json.append("    \"statement_results\": [\n");
        
        for (int i = 0; i < allResults.size(); i++) {
            List<Map<String, String>> result = allResults.get(i);
            json.append("      {\n");
            json.append("        \"statement_index\": ").append(i + 1).append(",\n");
            json.append("        \"row_count\": ").append(result.size()).append(",\n");
            json.append("        \"rows\": [\n");
            
            for (int j = 0; j < result.size(); j++) {
                Map<String, String> row = result.get(j);
                json.append("          {\n");
                int colIndex = 0;
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    json.append("            \"").append(escapeJsonString(entry.getKey())).append("\": \"")
                        .append(escapeJsonString(entry.getValue())).append("\"");
                    if (colIndex < row.size() - 1) json.append(",");
                    json.append("\n");
                    colIndex++;
                }
                json.append("          }");
                if (j < result.size() - 1) json.append(",");
                json.append("\n");
            }
            
            json.append("        ]\n");
            json.append("      }");
            if (i < allResults.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("    ],\n");
        json.append("    \"warning\": \"此接口存在高危SQL注入漏洞（真实数据库执行）！\"\n");
        json.append("  }\n");
        json.append("}");
        return json.toString();
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
            String sql = "SELECT id, username, password, phone, create_time FROM sys_user WHERE id = ?";
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
                // 处理create_time字段，可能为null
                Object createTimeObj = row.get("create_time");
                String createTimeStr = createTimeObj != null ? createTimeObj.toString() : "";
                user.put("create_time", createTimeStr);
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
        json.append("    \"executed_sql\": \"").append(escapeJsonString(sql)).append("\",\n");
        json.append("    \"user_list\": [");
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> user = data.get(i);
            json.append("\n      {\n");
            json.append("        \"id\": \"").append(escapeJsonString(user.get("id"))).append("\",\n");
            json.append("        \"username\": \"").append(escapeJsonString(user.get("username"))).append("\",\n");
            json.append("        \"password\": \"").append(escapeJsonString(user.get("password"))).append("\",\n");
            json.append("        \"phone\": \"").append(escapeJsonString(user.get("phone"))).append("\",\n");
            json.append("        \"create_time\": \"").append(escapeJsonString(user.get("create_time"))).append("\"\n");
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
     * 构造错误响应 - 增强版本，提供更多有用信息
     */
    private String buildErrorResponse(String errorMsg) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"code\": 500,\n");
        json.append("  \"msg\": \"SQL执行失败\",\n");
        json.append("  \"data\": {\n");
        json.append("    \"error_type\": \"").append(getErrorType(errorMsg)).append("\",\n");
        json.append("    \"error_message\": \"").append(escapeJsonString(errorMsg)).append("\"\n");
        json.append("  },\n");
        json.append("  \"warning\": \"注入攻击触发真实数据库异常！\"\n");
        json.append("}");
        return json.toString();
    }

    /**
     * 根据错误信息判断错误类型
     */
    private String getErrorType(String errorMsg) {
        if (errorMsg == null) return "UNKNOWN";
        
        String errorLower = errorMsg.toLowerCase();
        if (errorLower.contains("syntax")) {
            return "SYNTAX_ERROR";
        } else if (errorLower.contains("access denied")) {
            return "PERMISSION_DENIED";
        } else if (errorLower.contains("table") && errorLower.contains("doesn't exist")) {
            return "TABLE_NOT_FOUND";
        } else if (errorLower.contains("column") && errorLower.contains("doesn't exist")) {
            return "COLUMN_NOT_FOUND";
        } else if (errorLower.contains("different number of columns")) {
            return "UNION_COLUMN_MISMATCH";
        } else {
            return "OTHER_DATABASE_ERROR";
        }
    }

    /**
     * JSON字符串转义工具方法
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}