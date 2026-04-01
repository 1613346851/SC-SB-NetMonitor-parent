package com.network.monitor.service.impl;

import com.network.monitor.config.EmailConfig;
import com.network.monitor.config.FeishuConfig;
import com.network.monitor.entity.AlertEntity;
import com.network.monitor.service.NotificationService;
import com.network.monitor.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private EmailConfig emailConfig;

    @Autowired
    private FeishuConfig feishuConfig;

    @Autowired
    private SysConfigService sysConfigService;

    private JavaMailSender mailSender;

    @Override
    public boolean sendEmail(AlertEntity alert) {
        if (!isEmailEnabled()) {
            logger.debug("邮件通知已禁用");
            return false;
        }

        try {
            JavaMailSender sender = getMailSender();
            if (sender == null) {
                logger.warn("邮件发送器未初始化");
                return false;
            }

            MimeMessageHelper helper = new MimeMessageHelper(sender.createMimeMessage(), true, "UTF-8");
            helper.setFrom(getFromAddress());
            helper.setTo(getToAddresses().split(","));
            helper.setSubject(buildEmailSubject(alert));
            helper.setText(buildEmailContent(alert), true);

            sender.send(helper.getMimeMessage());
            logger.info("邮件通知发送成功: alertId={}", alert.getAlertId());
            return true;
        } catch (Exception e) {
            logger.error("邮件通知发送失败: alertId={}, error={}", alert.getAlertId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendFeishu(AlertEntity alert) {
        if (!isFeishuEnabled()) {
            logger.debug("飞书通知已禁用");
            return false;
        }

        try {
            String webhookUrl = getFeishuWebhookUrl();
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                logger.warn("飞书Webhook URL未配置");
                return false;
            }

            String body = buildFeishuMessage(alert);
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String sign = generateFeishuSign(timestamp);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<String> request = 
                new org.springframework.http.HttpEntity<>(body, headers);

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<String> response = 
                restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("飞书通知发送成功: alertId={}", alert.getAlertId());
                return true;
            } else {
                logger.warn("飞书通知发送失败: alertId={}, status={}", alert.getAlertId(), response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("飞书通知发送失败: alertId={}, error={}", alert.getAlertId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendTestEmail() {
        AlertEntity testAlert = new AlertEntity();
        testAlert.setAlertId("TEST-" + System.currentTimeMillis());
        testAlert.setAlertLevel("HIGH");
        testAlert.setAlertTitle("测试告警邮件");
        testAlert.setAlertContent("这是一封测试告警邮件，用于验证邮件配置是否正确。");
        testAlert.setSourceIp("127.0.0.1");
        testAlert.setAttackType("TEST");
        testAlert.setCreateTime(LocalDateTime.now());
        return sendEmail(testAlert);
    }

    @Override
    public boolean sendTestFeishu() {
        AlertEntity testAlert = new AlertEntity();
        testAlert.setAlertId("TEST-" + System.currentTimeMillis());
        testAlert.setAlertLevel("HIGH");
        testAlert.setAlertTitle("测试飞书告警");
        testAlert.setAlertContent("这是一条测试飞书告警消息，用于验证飞书配置是否正确。");
        testAlert.setSourceIp("127.0.0.1");
        testAlert.setAttackType("TEST");
        testAlert.setCreateTime(LocalDateTime.now());
        return sendFeishu(testAlert);
    }

    @Override
    public boolean isEmailEnabled() {
        String enabled = sysConfigService.getConfigValue("alert.email.enabled");
        if (enabled != null) {
            return "true".equalsIgnoreCase(enabled);
        }
        return emailConfig.getEnabled() != null && emailConfig.getEnabled();
    }

    @Override
    public boolean isFeishuEnabled() {
        String enabled = sysConfigService.getConfigValue("alert.feishu.enabled");
        if (enabled != null) {
            return "true".equalsIgnoreCase(enabled);
        }
        return feishuConfig.getEnabled() != null && feishuConfig.getEnabled();
    }

    private JavaMailSender getMailSender() {
        if (mailSender == null) {
            synchronized (this) {
                if (mailSender == null) {
                    mailSender = createMailSender();
                }
            }
        }
        return mailSender;
    }

    private JavaMailSender createMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(getSmtpHost());
        sender.setPort(getSmtpPort());
        sender.setUsername(getSmtpUsername());
        sender.setPassword(getSmtpPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (isSslEnabled()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.required", "true");
        }
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.connectiontimeout", "5000");

        return sender;
    }

    private String buildEmailSubject(AlertEntity alert) {
        return String.format("[网络监测系统] %s - %s", 
            alert.getAlertLevelChinese(), alert.getAlertTitle());
    }

    private String buildEmailContent(AlertEntity alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
        sb.append("<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>");
        
        sb.append("<div style='background-color: ").append(getLevelColor(alert.getAlertLevel()))
          .append("; color: white; padding: 15px; border-radius: 5px 5px 0 0;'>");
        sb.append("<h2 style='margin: 0;'>").append(alert.getAlertTitle()).append("</h2>");
        sb.append("</div>");
        
        sb.append("<div style='background-color: #f9f9f9; padding: 20px; border: 1px solid #ddd; border-top: none;'>");
        
        sb.append("<table style='width: 100%; border-collapse: collapse;'>");
        sb.append("<tr><td style='padding: 8px; border-bottom: 1px solid #eee; width: 120px;'><strong>告警级别</strong></td>");
        sb.append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>").append(alert.getAlertLevelChinese()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border-bottom: 1px solid #eee;'><strong>来源IP</strong></td>");
        sb.append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>").append(alert.getSourceIp()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border-bottom: 1px solid #eee;'><strong>攻击类型</strong></td>");
        sb.append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>").append(alert.getAttackTypeChinese()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border-bottom: 1px solid #eee;'><strong>发生时间</strong></td>");
        sb.append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>")
          .append(alert.getCreateTime() != null ? alert.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-").append("</td></tr>");
        sb.append("</table>");
        
        if (alert.getAlertContent() != null) {
            sb.append("<div style='margin-top: 15px; padding: 10px; background-color: #fff; border: 1px solid #ddd;'>");
            sb.append("<strong>详情：</strong><br/>").append(alert.getAlertContent().replace("\n", "<br/>"));
            sb.append("</div>");
        }
        
        sb.append("<div style='margin-top: 20px; text-align: center; color: #888;'>");
        sb.append("此邮件由网络监测系统自动发送，请勿直接回复。");
        sb.append("</div>");
        
        sb.append("</div></div></body></html>");
        return sb.toString();
    }

    private String buildFeishuMessage(AlertEntity alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"msg_type\":\"interactive\",");
        sb.append("\"card\":{");
        sb.append("\"header\":{");
        sb.append("\"title\":{").append("\"tag\":\"plain_text\",\"content\":\"").append(escapeJson(alert.getAlertTitle())).append("\"},");
        sb.append("\"template\":\"").append(getFeishuColor(alert.getAlertLevel())).append("\"");
        sb.append("},");
        sb.append("\"elements\":[");
        
        sb.append(buildFeishuField("告警级别", alert.getAlertLevelChinese()));
        sb.append(",");
        sb.append(buildFeishuField("来源IP", alert.getSourceIp()));
        sb.append(",");
        sb.append(buildFeishuField("攻击类型", alert.getAttackTypeChinese()));
        sb.append(",");
        sb.append(buildFeishuField("发生时间", alert.getCreateTime() != null ? alert.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-"));
        
        if (alert.getAlertContent() != null) {
            sb.append(",{\"tag\":\"div\",\"text\":{\"tag\":\"plain_text\",\"content\":\"").append(escapeJson(alert.getAlertContent())).append("\"}}");
        }
        
        sb.append("]}}");
        return sb.toString();
    }

    private String buildFeishuField(String name, String value) {
        return String.format("{\"tag\":\"div\",\"fields\":[{\"is_short\":true,\"text\":{\"tag\":\"plain_text\",\"content\":\"%s\"}},{\"is_short\":true,\"text\":{\"tag\":\"plain_text\",\"content\":\"%s\"}}]}", 
            escapeJson(name), escapeJson(value));
    }

    private String generateFeishuSign(String timestamp) {
        String secret = getFeishuSecret();
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(new byte[0]);
            return Base64.getEncoder().encodeToString(signData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("生成飞书签名失败", e);
            return "";
        }
    }

    private String getLevelColor(String level) {
        if (level == null) return "#3498db";
        switch (level) {
            case "CRITICAL": return "#e74c3c";
            case "HIGH": return "#e67e22";
            case "MEDIUM": return "#f1c40f";
            case "LOW": return "#3498db";
            default: return "#3498db";
        }
    }

    private String getFeishuColor(String level) {
        if (level == null) return "blue";
        switch (level) {
            case "CRITICAL": return "red";
            case "HIGH": return "orange";
            case "MEDIUM": return "yellow";
            case "LOW": return "blue";
            default: return "blue";
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String getSmtpHost() {
        String host = sysConfigService.getConfigValue("alert.email.smtp.host");
        return host != null ? host : emailConfig.getSmtpHost();
    }

    private int getSmtpPort() {
        String port = sysConfigService.getConfigValue("alert.email.smtp.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return emailConfig.getSmtpPort() != null ? emailConfig.getSmtpPort() : 465;
    }

    private String getSmtpUsername() {
        String username = sysConfigService.getConfigValue("alert.email.smtp.username");
        return username != null ? username : emailConfig.getSmtpUsername();
    }

    private String getSmtpPassword() {
        String password = sysConfigService.getConfigValue("alert.email.smtp.password");
        return password != null ? password : emailConfig.getSmtpPassword();
    }

    private boolean isSslEnabled() {
        String ssl = sysConfigService.getConfigValue("alert.email.smtp.ssl-enabled");
        if (ssl != null) {
            return "true".equalsIgnoreCase(ssl);
        }
        return emailConfig.getSslEnabled() == null || emailConfig.getSslEnabled();
    }

    private String getFromAddress() {
        String from = sysConfigService.getConfigValue("alert.email.from-address");
        return from != null ? from : emailConfig.getFromAddress();
    }

    private String getToAddresses() {
        String to = sysConfigService.getConfigValue("alert.email.to-addresses");
        return to != null ? to : emailConfig.getToAddresses();
    }

    private String getFeishuWebhookUrl() {
        String url = sysConfigService.getConfigValue("alert.feishu.webhook-url");
        return url != null ? url : feishuConfig.getWebhookUrl();
    }

    private String getFeishuSecret() {
        String secret = sysConfigService.getConfigValue("alert.feishu.secret");
        return secret != null ? secret : feishuConfig.getSecret();
    }
}
