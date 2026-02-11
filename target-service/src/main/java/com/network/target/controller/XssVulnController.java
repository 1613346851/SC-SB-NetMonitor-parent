package com.network.target.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * XSS漏洞接口（预设漏洞）
 * 接口路径：/target/xss/**，支持GET/POST请求
 */
@RestController
@RequestMapping("/target/xss")
@Slf4j
public class XssVulnController {

    /**
     * 模拟“评论提交”接口（存在XSS漏洞）
     * 漏洞点：直接返回用户输入的HTML/JS内容，未做转义
     * 测试攻击请求：POST提交 {"content":"<script>alert('XSS攻击')</script>"}
     */
    @PostMapping("/submit-comment")
    public String submitComment(@RequestBody CommentDTO commentDTO) {
        // 1. 日志记录用户输入（含恶意脚本）
        log.info("【XSS漏洞接口】接收评论内容：{}", commentDTO.getContent());

        // 2. 直接返回用户输入（漏洞核心：未转义HTML标签）
        return "{\n" +
                "  \"code\": 200,\n" +
                "  \"msg\": \"评论提交成功（模拟）\",\n" +
                "  \"data\": {\n" +
                "    \"comment_id\": \"1001\",\n" +
                "    \"comment_content\": \"" + commentDTO.getContent() + "\",\n" +
                "    \"warning\": \"此接口存在XSS漏洞，未转义HTML/JS内容\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * 评论DTO（用于接收POST请求参数）
     */
    @Setter
    @Getter
    public static class CommentDTO {
        // Lombok可简化get/set，此处手动实现兼容无Lombok场景
        private String content; // 评论内容（可能含恶意脚本）

    }

}