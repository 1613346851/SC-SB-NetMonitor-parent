package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSRF跨站请求伪造漏洞测试接口
 * 核心：模拟用户昵称修改功能，接口无CSRF Token校验
 */
@RestController
@RequestMapping("/target/csrf")
@Slf4j
public class CsrfController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> csrfTokens = new ConcurrentHashMap<>();
    
    private final Map<Integer, Map<String, Object>> userSession = new ConcurrentHashMap<>();
    
    {
        Map<String, Object> adminSession = new HashMap<>();
        adminSession.put("userId", 1);
        adminSession.put("username", "admin");
        adminSession.put("nickname", "超级管理员");
        adminSession.put("email", "admin@example.com");
        userSession.put(1, adminSession);
    }

    /**
     * 漏洞接口：无CSRF防护的昵称修改
     * 攻击场景：攻击者可构造恶意页面，诱导用户点击后自动提交修改请求
     */
    @PostMapping("/update-name")
    public ApiResponse updateNicknameVulnerable(
            @RequestParam("userId") Integer userId,
            @RequestParam("nickname") String nickname) {
        try {
            log.warn("【高危CSRF漏洞】尝试修改用户昵称 - userId: {}, nickname: {}", userId, nickname);

            Map<String, Object> user = userSession.getOrDefault(userId, new HashMap<>());
            String oldNickname = (String) user.getOrDefault("nickname", "未知用户");
            
            user.put("userId", userId);
            user.put("nickname", nickname);
            userSession.put(userId, user);

            try {
                String updateSql = "UPDATE sys_user_config SET nickname = ? WHERE user_id = ?";
                jdbcTemplate.update(updateSql, nickname, userId);
            } catch (Exception e) {
                log.warn("数据库更新失败，使用内存存储: {}", e.getMessage());
            }

            log.warn("【CSRF漏洞触发】昵称修改成功 - userId: {}, old: {}, new: {}", userId, oldNickname, nickname);

            return ApiResponse.success()
                    .message("昵称修改成功（漏洞接口）")
                    .data("user_id", userId)
                    .data("old_nickname", oldNickname)
                    .data("new_nickname", nickname)
                    .data("warning", "CSRF漏洞：无Token校验，攻击者可伪造请求修改用户信息！");

        } catch (Exception e) {
            log.error("CSRF昵称修改异常", e);
            return ApiResponse.error()
                    .message("修改失败：" + e.getMessage());
        }
    }

    /**
     * 安全接口：携带Token校验
     * 防护措施：
     * 1. 生成并验证CSRF Token
     * 2. Token与用户会话绑定
     * 3. Token一次性使用或有效期限制
     */
    @PostMapping("/safe-update-name")
    public ApiResponse updateNicknameSafe(
            @RequestParam("userId") Integer userId,
            @RequestParam("nickname") String nickname,
            @RequestParam("token") String token) {
        try {
            log.info("【安全接口】尝试修改用户昵称 - userId: {}, nickname: {}", userId, nickname);

            String expectedToken = csrfTokens.get(String.valueOf(userId));
            
            if (expectedToken == null || !expectedToken.equals(token)) {
                log.warn("【安全拦截】CSRF Token验证失败 - userId: {}, token: {}", userId, token);
                return ApiResponse.error()
                        .message("CSRF Token验证失败")
                        .data("blocked_reason", "Token无效或已过期，请刷新页面获取新Token");
            }

            Map<String, Object> user = userSession.getOrDefault(userId, new HashMap<>());
            String oldNickname = (String) user.getOrDefault("nickname", "未知用户");
            
            user.put("userId", userId);
            user.put("nickname", nickname);
            userSession.put(userId, user);

            csrfTokens.remove(String.valueOf(userId));

            log.info("【安全接口】昵称修改成功 - userId: {}, old: {}, new: {}", userId, oldNickname, nickname);

            return ApiResponse.success()
                    .message("昵称修改成功（安全接口）")
                    .data("user_id", userId)
                    .data("old_nickname", oldNickname)
                    .data("new_nickname", nickname)
                    .data("security_note", "已通过CSRF Token校验");

        } catch (Exception e) {
            log.error("安全CSRF昵称修改异常", e);
            return ApiResponse.error()
                    .message("修改失败：" + e.getMessage());
        }
    }

    /**
     * 获取CSRF Token
     */
    @GetMapping("/get-token")
    public ApiResponse getCsrfToken(@RequestParam("userId") Integer userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        csrfTokens.put(String.valueOf(userId), token);
        
        log.info("生成CSRF Token - userId: {}, token: {}", userId, token);

        return ApiResponse.success()
                .message("获取Token成功")
                .data("user_id", userId)
                .data("csrf_token", token);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/user-info")
    public ApiResponse getUserInfo(@RequestParam(value = "userId", defaultValue = "1") Integer userId) {
        Map<String, Object> user = userSession.getOrDefault(userId, new HashMap<>());
        
        if (user.isEmpty()) {
            user.put("userId", userId);
            user.put("username", "user_" + userId);
            user.put("nickname", "用户" + userId);
            user.put("email", "user" + userId + "@example.com");
        }

        return ApiResponse.success()
                .message("获取用户信息成功")
                .data("user", user);
    }

    /**
     * 获取攻击演示页面HTML
     */
    @GetMapping("/attack-page")
    public String getAttackPage() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>恭喜您中奖了！</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }\n" +
                "        .prize { color: red; font-size: 24px; font-weight: bold; }\n" +
                "        .btn { background: #ff6600; color: white; padding: 15px 30px; border: none; cursor: pointer; font-size: 18px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>🎉 恭喜您！您中奖了！</h1>\n" +
                "    <p class=\"prize\">奖品：iPhone 15 Pro Max</p>\n" +
                "    <p>点击下方按钮领取奖品</p>\n" +
                "    <form id=\"csrfForm\" action=\"http://localhost:9001/target/csrf/update-name\" method=\"POST\">\n" +
                "        <input type=\"hidden\" name=\"userId\" value=\"1\">\n" +
                "        <input type=\"hidden\" name=\"nickname\" value=\"CSRF攻击成功_被劫持的用户\">\n" +
                "        <button type=\"submit\" class=\"btn\">立即领取</button>\n" +
                "    </form>\n" +
                "    <p style=\"color: red; margin-top: 20px;\">\n" +
                "        ⚠️ 这是一个CSRF攻击演示页面！<br>\n" +
                "        点击按钮将自动修改靶场用户的昵称\n" +
                "    </p>\n" +
                "</body>\n" +
                "</html>";
    }
}
