package com.network.target.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL注入漏洞接口（高度模拟真实生产环境漏洞）
 * 核心特征：
 * 1. 用内存模拟数据库，注入成功可获取敏感数据（而非仅返回SQL语句）
 * 2. 支持联合查询、报错注入、布尔盲注、时间盲注等真实攻击场景
 * 3. 无任何输入过滤/参数化查询，完全还原漏洞成因
 * 接口路径：/target/sql/**，兼容网关路由配置
 */
@RestController
@RequestMapping("/target/sql")
@Slf4j
public class SqlVulnController {

    // 模拟数据库：用户表（含敏感数据：密码、手机号）
    private static final List<Map<String, String>> USER_DB = new ArrayList<>();
    // 模拟初始化数据库数据
    static {
        Map<String, String> user1 = new HashMap<>();
        user1.put("id", "1");
        user1.put("username", "admin");
        user1.put("password", "admin@123"); // 敏感密码
        user1.put("phone", "13800138000"); // 敏感手机号
        USER_DB.add(user1);

        Map<String, String> user2 = new HashMap<>();
        user2.put("id", "2");
        user2.put("username", "test");
        user2.put("password", "test@456");
        user2.put("phone", "13900139000");
        USER_DB.add(user2);
    }

    /**
     * 模拟“用户查询”接口（真实SQL注入漏洞场景）
     * 漏洞点：直接拼接用户输入到SQL，无参数化查询/过滤
     * 支持的攻击场景：
     * 1. 联合查询注入：?id=1 union select 3,'hack','hack@789','13700137000'
     * 2. 报错注入：?id=1 and updatexml(1,concat(0x7e,(select password from sys_user where id=1),0x7e),1)
     * 3. 布尔盲注：?id=1 and (length((select username from sys_user where id=1))=5)
     * 4. 时间盲注：?id=1 and sleep(3)
     */
    @GetMapping("/query")
    public String queryUser(@RequestParam("id") String userId) {
        try {
            // 1. 漏洞核心：无过滤拼接用户输入到SQL（真实场景最常见的漏洞成因）
            String sql = "SELECT id, username, password, phone FROM sys_user WHERE id = " + userId;
            log.warn("【高危SQL注入漏洞】执行未过滤的SQL：{}", sql);

            // 2. 模拟真实数据库查询逻辑（根据拼接后的SQL返回数据，而非仅返回SQL）
            List<Map<String, String>> queryResult = simulateDbQuery(sql);

            // 3. 构造真实业务返回格式（包含数据/状态码，而非仅提示）
            return buildSuccessResponse(queryResult, sql);

        } catch (Exception e) {
            // 4. 模拟SQL执行报错（支撑报错注入场景）
            log.error("【SQL执行异常】注入攻击触发报错：", e);
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * 模拟数据库查询逻辑（解析拼接后的SQL，返回对应结果，支撑注入效果）
     */
    private List<Map<String, String>> simulateDbQuery(String sql) {
        List<Map<String, String>> result = new ArrayList<>();

        // 场景1：检测联合查询注入（union select）
        if (sql.toLowerCase().contains("union select")) {
            // 解析union后的字段（简化模拟，取固定位置的注入数据）
            String[] parts = sql.toLowerCase().split("union select")[1].trim().split(",");
            if (parts.length >= 4) {
                Map<String, String> injectRow = new HashMap<>();
                injectRow.put("id", parts[0].trim());
                injectRow.put("username", parts[1].replace("'", "").trim());
                injectRow.put("password", parts[2].replace("'", "").trim());
                injectRow.put("phone", parts[3].replace("'", "").trim());
                result.add(injectRow);
            }
            // 保留原始查询结果+注入结果（模拟联合查询效果）
            result.addAll(getUserById(getOriginalId(sql)));
            return result;
        }

        // 场景2：检测报错注入（updatexml/extractvalue）
        if (sql.toLowerCase().contains("updatexml") || sql.toLowerCase().contains("extractvalue")) {
            throw new RuntimeException("XPATH syntax error: '~" + getSensitiveData() + "~'");
        }

        // 场景3：检测布尔盲注（and 条件）
        if (sql.toLowerCase().contains("and") && sql.toLowerCase().contains("length")) {
            // 模拟布尔盲注结果：条件为真返回数据，假返回空
            boolean conditionTrue = checkBlindInjectionCondition(sql);
            return conditionTrue ? getUserById(getOriginalId(sql)) : new ArrayList<>();
        }

        // 场景4：检测时间盲注（sleep）
        if (sql.toLowerCase().contains("sleep")) {
            try {
                // 提取sleep时长并模拟延迟（支撑时间盲注效果）
                long sleepTime = Long.parseLong(sql.toLowerCase().split("sleep\\(")[1].split("\\)")[0].trim());
                Thread.sleep(sleepTime * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getUserById(getOriginalId(sql));
        }

        // 场景5：普通查询（无注入）
        return getUserById(getOriginalId(sql));
    }

    /**
     * 辅助：提取原始查询的ID（过滤注入语句，获取基础查询条件）
     */
    private String getOriginalId(String sql) {
        // 取WHERE id = 后的第一个值（简化模拟）
        String idPart = sql.split("WHERE id = ")[1].trim();
        if (idPart.contains(" ")) {
            return idPart.split(" ")[0];
        }
        return idPart;
    }

    /**
     * 辅助：根据ID查询模拟数据库中的用户（普通查询逻辑）
     */
    private List<Map<String, String>> getUserById(String id) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> user : USER_DB) {
            if (user.get("id").equals(id)) {
                result.add(user);
                break;
            }
        }
        return result;
    }

    /**
     * 辅助：获取敏感数据（支撑报错注入场景，模拟泄露密码）
     */
    private String getSensitiveData() {
        return USER_DB.get(0).get("password"); // 泄露admin的密码
    }

    /**
     * 辅助：检测布尔盲注条件是否为真（简化模拟）
     */
    private boolean checkBlindInjectionCondition(String sql) {
        // 模拟：判断admin用户名长度是否为5（实际场景可扩展更多条件）
        return sql.contains("length((select username from sys_user where id=1))=5");
    }

    /**
     * 构造成功响应（贴合真实业务返回格式）
     */
    private String buildSuccessResponse(List<Map<String, String>> data, String sql) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"code\": 200,\n");
        json.append("  \"msg\": \"用户查询成功\",\n");
        json.append("  \"data\": {\n");
        json.append("    \"executed_sql\": \"").append(sql.replace("\"", "\\\"")).append("\",\n");
        json.append("    \"user_list\": [");

        // 拼接用户数据
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> user = data.get(i);
            json.append("\n      {\n");
            json.append("        \"id\": \"").append(user.get("id")).append("\",\n");
            json.append("        \"username\": \"").append(user.get("username")).append("\",\n");
            json.append("        \"password\": \"").append(user.get("password")).append("\",\n");
            json.append("        \"phone\": \"").append(user.get("phone")).append("\"\n");
            json.append("      }");
            if (i < data.size() - 1) {
                json.append(",");
            }
        }

        json.append("\n    ],\n");
        json.append("    \"warning\": \"此接口存在高危SQL注入漏洞，未使用参数化查询！\"\n");
        json.append("  }\n");
        json.append("}");
        return json.toString();
    }

    /**
     * 构造错误响应（支撑报错注入场景）
     */
    private String buildErrorResponse(String errorMsg) {
        return "{\n" +
                "  \"code\": 500,\n" +
                "  \"msg\": \"SQL执行失败\",\n" +
                "  \"error\": \"" + errorMsg.replace("\"", "\\\"") + "\",\n" +
                "  \"warning\": \"报错注入攻击触发SQL执行异常！\"\n" +
                "}";
    }
}