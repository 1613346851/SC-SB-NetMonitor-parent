package com.network.monitor.websocket;

import com.network.monitor.entity.AlertEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertPushService {

    private static final Logger logger = LoggerFactory.getLogger(AlertPushService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void pushAlert(AlertEntity alert) {
        try {
            AlertMessage message = convertToMessage(alert);
            messagingTemplate.convertAndSend("/topic/alerts", message);
            logger.info("告警已推送: alertId={}, level={}, sourceIp={}", 
                alert.getAlertId(), alert.getAlertLevel(), alert.getSourceIp());
        } catch (Exception e) {
            logger.error("推送告警失败: alertId={}", alert.getAlertId(), e);
        }
    }

    private AlertMessage convertToMessage(AlertEntity entity) {
        return AlertMessage.builder()
            .id(entity.getId())
            .alertId(entity.getAlertId())
            .eventId(entity.getEventId())
            .attackId(entity.getAttackId())
            .sourceIp(entity.getSourceIp())
            .attackType(entity.getAttackType())
            .alertLevel(entity.getAlertLevel())
            .alertTitle(entity.getAlertTitle())
            .alertContent(entity.getAlertContent())
            .status(entity.getStatus())
            .createTime(entity.getCreateTime())
            .build();
    }
}
