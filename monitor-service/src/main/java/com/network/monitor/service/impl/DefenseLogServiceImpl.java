package com.network.monitor.service.impl;

import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.event.BlacklistSyncEvent;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.IpBlacklistHistoryMapper;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DefenseLogServiceImpl implements DefenseLogService {

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private IpBlacklistHistoryMapper ipBlacklistHistoryMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AttackEventService attackEventService;

    @Override
    public void receiveDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }

        try {
            DefenseLogEntity entity = convertToEntity(logDTO);
            
            defenseLogMapper.insert(entity);
            
            log.info("接收并保存防御日志成功：defenseType={}, defenseTarget={}, defenseAction={}, executeStatus={}, isFirst={}", 
                logDTO.getDefenseType(), logDTO.getDefenseTarget(), logDTO.getDefenseAction(), logDTO.getExecuteStatus(), entity.getIsFirst());

            if (logDTO.getExecuteStatus() != null && logDTO.getExecuteStatus() == 1 && entity.getIsFirst() == 1) {
                publishBlacklistSyncEvent(logDTO);
            } else if (entity.getIsFirst() == 0) {
                log.debug("非首次防御日志，跳过黑名单事件发布：eventId={}", entity.getEventId());
            }
            
        } catch (Exception e) {
            log.error("接收防御日志失败：", e);
        }
    }

    private void publishBlacklistSyncEvent(DefenseLogDTO logDTO) {
        String defenseType = logDTO.getDefenseType();
        String defenseAction = logDTO.getDefenseAction();
        String targetIp = logDTO.getDefenseTarget();

        if (targetIp == null || targetIp.isEmpty()) {
            return;
        }

        boolean isBlacklistAction = "ADD_BLACKLIST".equals(defenseAction) || 
                                    "BLACKLIST".equals(defenseAction) ||
                                    "BLOCK_IP".equals(defenseType) ||
                                    "BLACKLIST".equals(defenseType);
        
        boolean isRemoveAction = "REMOVE".equals(defenseAction) || 
                                "REMOVE_BLACKLIST".equals(defenseAction);

        if (isBlacklistAction) {
            LocalDateTime expireTime = parseExpireTime(logDTO.getExpireTime());
            String reason = logDTO.getDefenseReason() != null ? logDTO.getDefenseReason() : "网关防御自动添加";
            String operator = logDTO.getOperator() != null ? logDTO.getOperator() : "GATEWAY";

            BlacklistSyncEvent event = BlacklistSyncEvent.add(this, targetIp, reason, expireTime, operator);
            eventPublisher.publishEvent(event);
            log.info("发布黑名单添加事件：ip={}, reason={}", targetIp, reason);

        } else if (isRemoveAction) {
            BlacklistSyncEvent event = BlacklistSyncEvent.remove(this, targetIp);
            eventPublisher.publishEvent(event);
            log.info("发布黑名单移除事件：ip={}", targetIp);
        }
    }

    private LocalDateTime parseExpireTime(String expireTimeStr) {
        if (expireTimeStr == null || expireTimeStr.isEmpty()) {
            return null;
        }
        try {
            if (expireTimeStr.contains("T")) {
                return LocalDateTime.parse(expireTimeStr.substring(0, 19));
            } else {
                return LocalDateTime.parse(expireTimeStr.replace(" ", "T").substring(0, 19));
            }
        } catch (Exception e) {
            log.warn("解析过期时间失败：{}", expireTimeStr);
            return null;
        }
    }

    @Override
    public void saveDefenseLog(DefenseLogEntity entity) {
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        if (entity.getExecuteTime() == null) {
            entity.setExecuteTime(LocalDateTime.now());
        }
        defenseLogMapper.insert(entity);
    }

    private DefenseLogEntity convertToEntity(DefenseLogDTO dto) {
        DefenseLogEntity entity = new DefenseLogEntity();
        
        entity.setDefenseType(convertDefenseType(dto.getDefenseType()));
        entity.setDefenseAction(dto.getDefenseAction());
        entity.setDefenseTarget(dto.getDefenseTarget());
        entity.setAttackId(dto.getAttackId());
        entity.setTrafficId(dto.getTrafficId());
        entity.setDefenseReason(dto.getDefenseReason());
        entity.setExecuteStatus(dto.getExecuteStatus());
        entity.setExecuteResult(dto.getExecuteResult());
        entity.setOperator(dto.getOperator() != null ? dto.getOperator() : "SYSTEM");
        
        entity.setExpireTime(parseExpireTime(dto.getExpireTime()));

        if (dto.getEventId() != null && !dto.getEventId().isEmpty()) {
            entity.setEventId(dto.getEventId());
        } else if (dto.getDefenseTarget() != null && !dto.getDefenseTarget().isEmpty()) {
            AttackEventEntity ongoingEvent = attackEventService.getOngoingEventByIp(dto.getDefenseTarget());
            if (ongoingEvent != null) {
                entity.setEventId(ongoingEvent.getEventId());
            }
        }

        boolean isBlacklistAction = "ADD_BLACKLIST".equals(dto.getDefenseAction()) || 
                                    "BLACKLIST".equals(dto.getDefenseAction()) ||
                                    "BLOCK_IP".equals(dto.getDefenseType());
        
        if (isBlacklistAction && dto.getDefenseTarget() != null && !dto.getDefenseTarget().isEmpty()) {
            int historyCount = ipBlacklistHistoryMapper.countByIp(dto.getDefenseTarget());
            entity.setIsFirst(historyCount == 0 ? 1 : 0);
            
            if (historyCount > 0) {
                entity.setDefenseAction("ADD_BLACKLIST_REPEAT");
                log.info("IP重复封禁，设置defenseAction=ADD_BLACKLIST_REPEAT: ip={}, historyCount={}", 
                    dto.getDefenseTarget(), historyCount);
            }
        } else if (entity.getEventId() != null) {
            int existingCount = defenseLogMapper.countByEventId(entity.getEventId());
            entity.setIsFirst(existingCount == 0 ? 1 : 0);
        } else {
            entity.setIsFirst(0);
        }
        
        LocalDateTime now = LocalDateTime.now();
        entity.setExecuteTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        
        return entity;
    }

    private String convertDefenseType(String originalType) {
        if (originalType == null) {
            return "BLOCK_IP";
        }
        
        return switch (originalType.toUpperCase()) {
            case "IP_BLOCK", "BLACKLIST" -> "BLOCK_IP";
            case "RATE_LIMIT" -> "RATE_LIMIT";
            case "MALICIOUS_REQUEST", "BLOCK" -> "BLOCK_REQUEST";
            default -> originalType.toUpperCase();
        };
    }
}
