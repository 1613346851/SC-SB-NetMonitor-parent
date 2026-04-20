package com.network.monitor.websocket;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AlertEntity;
import com.network.monitor.entity.AttackMonitorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DataPushService {

    private static final Logger logger = LoggerFactory.getLogger(DataPushService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void pushAttackRecord(AttackMonitorEntity attack) {
        try {
            Map<String, Object> attackData = convertAttackToMap(attack);
            DataUpdateMessage message = DataUpdateMessage.attackRecord(attackData);
            messagingTemplate.convertAndSend("/topic/data", message);
            logger.debug("攻击记录已推送: attackId={}, sourceIp={}", attack.getId(), attack.getSourceIp());
        } catch (Exception e) {
            logger.error("推送攻击记录失败: attackId={}", attack.getId(), e);
        }
    }

    public void pushAlertRecord(AlertEntity alert) {
        try {
            Map<String, Object> alertData = convertAlertToMap(alert);
            DataUpdateMessage message = DataUpdateMessage.alertRecord(alertData);
            messagingTemplate.convertAndSend("/topic/data", message);
            logger.debug("告警记录已推送: alertId={}, level={}", alert.getAlertId(), alert.getAlertLevel());
        } catch (Exception e) {
            logger.error("推送告警记录失败: alertId={}", alert.getAlertId(), e);
        }
    }

    public void pushStatsUpdate(Map<String, Object> stats) {
        try {
            DataUpdateMessage message = DataUpdateMessage.statsUpdate(stats);
            messagingTemplate.convertAndSend("/topic/data", message);
            logger.debug("统计数据已推送");
        } catch (Exception e) {
            logger.error("推送统计数据失败", e);
        }
    }

    public void pushEventStatsUpdate(Map<String, Object> eventStats) {
        try {
            DataUpdateMessage message = DataUpdateMessage.eventStatsUpdate(eventStats);
            messagingTemplate.convertAndSend("/topic/data", message);
            logger.debug("事件统计已推送");
        } catch (Exception e) {
            logger.error("推送事件统计失败", e);
        }
    }

    private Map<String, Object> convertAttackToMap(AttackMonitorEntity attack) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", attack.getId());
        map.put("eventId", attack.getEventId());
        map.put("sourceIp", attack.getSourceIp());
        map.put("targetUri", attack.getTargetUri());
        map.put("attackType", attack.getAttackType());
        map.put("riskLevel", attack.getRiskLevel());
        map.put("confidence", attack.getConfidence());
        map.put("handled", attack.getHandled());
        map.put("createTime", attack.getCreateTime());
        return map;
    }

    private Map<String, Object> convertAlertToMap(AlertEntity alert) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", alert.getId());
        map.put("alertId", alert.getAlertId());
        map.put("eventId", alert.getEventId());
        map.put("attackId", alert.getAttackId());
        map.put("sourceIp", alert.getSourceIp());
        map.put("attackType", alert.getAttackType());
        map.put("alertLevel", alert.getAlertLevel());
        map.put("alertTitle", alert.getAlertTitle());
        map.put("status", alert.getStatus());
        map.put("createTime", alert.getCreateTime());
        return map;
    }
}
