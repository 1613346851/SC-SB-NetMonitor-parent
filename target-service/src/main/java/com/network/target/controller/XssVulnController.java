package com.network.target.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * XSS漏洞接口（基于真实MySQL数据库，贴合生产环境）
 * 核心：存储型XSS依赖数据库，反射型/DOM型作为补充
 */
@RestController
@RequestMapping("/target/xss")
@Slf4j
public class XssVulnController {

    // 注入Spring配置的JdbcTemplate（复用SQL注入模块的MySQL配置）
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 场景1：存储型XSS（核心，依赖真实数据库）
     * 漏洞点：恶意脚本存入MySQL，查询时直接返回未转义的内容
     * 攻击效果：所有查询评论的用户都会触发XSS
     */
    @PostMapping("/submit-comment")
    public String submitComment(@RequestBody CommentDTO commentDTO) {
        try {
            // 1. 模拟当前登录用户（生产环境从Token/会话获取）
            String currentUser = "test_user_" + System.currentTimeMillis() % 1000;
            // 2. 生成唯一评论ID
            String commentId = UUID.randomUUID().toString().replace("-", "");
            long createTime = System.currentTimeMillis();

            // 漏洞核心：直接将未过滤的恶意内容插入MySQL，无任何转义
            String insertSql = "INSERT INTO sys_comment (id, content, username, create_time) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, commentId, commentDTO.getContent(), currentUser, createTime);
            log.warn("【存储型XSS漏洞】向MySQL插入未过滤的恶意评论：{}，评论ID：{}", commentDTO.getContent(), commentId);

            // 3. 返回包含恶意内容的响应
            return "{\n" +
                    "  \"code\": 200,\n" +
                    "  \"msg\": \"评论提交成功\",\n" +
                    "  \"data\": {\n" +
                    "    \"comment_id\": \"" + commentId + "\",\n" +
                    "    \"comment_content\": \"" + commentDTO.getContent() + "\",\n" +
                    "    \"user\": \"" + currentUser + "\",\n" +
                    "    \"warning\": \"存储型XSS漏洞：恶意内容已存入MySQL！\"\n" +
                    "  }\n" +
                    "}";
        } catch (Exception e) {
            log.error("提交评论异常（MySQL操作失败）", e);
            return buildErrorResponse("评论提交失败：" + e.getMessage());
        }
    }

    /**
     * 场景1配套：查询评论列表（从MySQL读取，触发存储型XSS）
     */
    @GetMapping("/list-comments")
    public String listComments() {
        try {
            // 从MySQL查询所有评论（包含恶意脚本）
            String querySql = "SELECT id, content, username, create_time FROM sys_comment ORDER BY create_time DESC";
            List<Map<String, Object>> commentList = jdbcTemplate.queryForList(querySql);

            StringBuilder response = new StringBuilder();
            response.append("{\n");
            response.append("  \"code\": 200,\n");
            response.append("  \"msg\": \"评论列表查询成功（来自MySQL）\",\n");
            response.append("  \"data\": {\n");
            response.append("    \"comment_list\": [");

            // 漏洞核心：直接遍历MySQL返回的内容，未转义拼接到响应
            for (int i = 0; i < commentList.size(); i++) {
                Map<String, Object> comment = commentList.get(i);
                response.append("\n      {\n");
                response.append("        \"comment_id\": \"").append(comment.get("id")).append("\",\n");
                response.append("        \"content\": \"").append(comment.get("content")).append("\",\n");
                response.append("        \"user\": \"").append(comment.get("username")).append("\",\n");
                response.append("        \"create_time\": \"").append(comment.get("create_time")).append("\"\n");
                response.append("      }");
                if (i < commentList.size() - 1) {
                    response.append(",");
                }
            }

            response.append("\n    ],\n");
            response.append("    \"warning\": \"存储型XSS漏洞：MySQL中的恶意脚本会在前端执行！\"\n");
            response.append("  }\n");
            response.append("}");
            return response.toString();
        } catch (Exception e) {
            log.error("查询评论列表异常", e);
            return buildErrorResponse("查询评论失败：" + e.getMessage());
        }
    }

    /**
     * 场景2：反射型XSS（无数据库依赖，URL参数直接反射）
     * 漏洞点：GET参数未转义，直接拼接到响应
     */
    @GetMapping("/search")
    public String search(@RequestParam("keyword") String keyword) {
        log.warn("【反射型XSS漏洞】接收未过滤的搜索关键词：{}", keyword);
        return "{\n" +
                "  \"code\": 200,\n" +
                "  \"msg\": \"搜索成功\",\n" +
                "  \"data\": {\n" +
                "    \"keyword\": \"" + keyword + "\",\n" +
                "    \"result_count\": 0,\n" +
                "    \"tip\": \"你搜索的关键词：" + keyword + " 暂无结果\",\n" +
                "    \"warning\": \"反射型XSS漏洞！\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * 场景3：DOM型XSS（无数据库依赖，返回未转义HTML片段）
     * 漏洞点：后端返回原始HTML，前端DOM渲染时执行脚本
     */
    @GetMapping("/profile")
    public String getUserProfile(@RequestParam("username") String username) {
        log.warn("【DOM型XSS漏洞】接收未过滤的用户名：{}", username);
        String htmlFragment = "<div class=\"profile\"><h3>用户资料</h3><p>用户名：" + username + "</p></div>";
        return "{\n" +
                "  \"code\": 200,\n" +
                "  \"msg\": \"获取资料成功\",\n" +
                "  \"data\": {\n" +
                "    \"html\": \"" + htmlFragment + "\",\n" +
                "    \"warning\": \"DOM型XSS漏洞：前端渲染时执行脚本！\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * 安全对比接口：存储评论时转义HTML特殊字符（防护存储型XSS）
     */
    @PostMapping("/safe-submit-comment")
    public String safeSubmitComment(@RequestBody CommentDTO commentDTO) {
        try {
            // 核心防护：转义HTML特殊字符（避免脚本执行）
            String safeContent = escapeHtml(commentDTO.getContent());
            String commentId = UUID.randomUUID().toString().replace("-", "");
            String currentUser = "safe_user_" + System.currentTimeMillis() % 1000;
            long createTime = System.currentTimeMillis();

            // 插入转义后的安全内容到MySQL
            String insertSql = "INSERT INTO sys_comment (id, content, username, create_time) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, commentId, safeContent, currentUser, createTime);
            log.info("【安全接口】向MySQL插入转义后的评论：{}，原内容：{}", safeContent, commentDTO.getContent());

            return "{\n" +
                    "  \"code\": 200,\n" +
                    "  \"msg\": \"评论提交成功（安全版）\",\n" +
                    "  \"data\": {\n" +
                    "    \"comment_id\": \"" + commentId + "\",\n" +
                    "    \"original_content\": \"" + commentDTO.getContent() + "\",\n" +
                    "    \"safe_content\": \"" + safeContent + "\",\n" +
                    "    \"tip\": \"已转义HTML特殊字符，防护存储型XSS攻击\"\n" +
                    "  }\n" +
                    "}";
        } catch (Exception e) {
            log.error("安全提交评论异常", e);
            return buildErrorResponse("安全提交评论失败：" + e.getMessage());
        }
    }

    // 工具方法：HTML特殊字符转义（XSS核心防护手段）
    private String escapeHtml(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("/", "&#x2F;");
    }

    // 工具方法：构建统一错误响应
    private String buildErrorResponse(String errorMsg) {
        return "{\n" +
                "  \"code\": 500,\n" +
                "  \"msg\": \"操作失败\",\n" +
                "  \"error\": \"" + errorMsg.replace("\"", "\\\"") + "\"\n" +
                "}";
    }

    // 评论请求DTO（接收前端提交的内容）
    @Setter
    @Getter
    public static class CommentDTO {
        private String content; // 评论内容（可能含恶意XSS脚本）
    }
}