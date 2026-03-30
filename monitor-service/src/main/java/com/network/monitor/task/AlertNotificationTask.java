package com.network.monitor.task;

import com.network.monitor.entity.AlertEntity;
import com.network.monitor.mapper.AlertMapper;
import com.network.monitor.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AlertNotificationTask {

    private static final Logger logger = LoggerFactory.getLogger(AlertNotificationTask.class);

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedRateString = "${alert.notification.interval-ms:30000}")
    public void sendPendingNotifications() {
        List<AlertEntity> alerts = alertMapper.selectUnnotifiedAlerts(10);
        
        if (alerts.isEmpty()) {
            return;
        }
        
        logger.info("开始处理待发送告警通知，共{}条", alerts.size());
        
        for (AlertEntity alert : alerts) {
            try {
                boolean success = sendNotification(alert);
                
                int notifyStatus = success ? 1 : 2;
                alertMapper.updateNotifyStatus(alert.getId(), notifyStatus, LocalDateTime.now());
                
                logger.info("告警通知处理完成: alertId={}, success={}", alert.getAlertId(), success);
            } catch (Exception e) {
                logger.error("告警通知处理失败: alertId={}, error={}", alert.getAlertId(), e.getMessage(), e);
                alertMapper.updateNotifyStatus(alert.getId(), 2, LocalDateTime.now());
            }
        }
        
        logger.info("本次处理告警通知完成: {} 条", alerts.size());
    }

    private boolean sendNotification(AlertEntity alert) {
        boolean emailSuccess = true;
        boolean feishuSuccess = true;
        
        String channels = alert.getNotifyChannels();
        if (channels == null) {
            channels = "EMAIL,FEISHU";
        }
        
        if (channels.contains("EMAIL") && notificationService.isEmailEnabled()) {
            emailSuccess = notificationService.sendEmail(alert);
        }
        
        if (channels.contains("FEISHU") && notificationService.isFeishuEnabled()) {
            feishuSuccess = notificationService.sendFeishu(alert);
        }
        
        return emailSuccess && feishuSuccess;
    }
}
