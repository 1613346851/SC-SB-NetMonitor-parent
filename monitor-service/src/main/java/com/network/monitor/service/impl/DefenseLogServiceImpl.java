package com.network.monitor.service.impl;

import com.network.monitor.dto.AlertDTO;
import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.event.BlacklistSyncEvent;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.service.AlertService;
import com.network.monitor.service.AttackEventService;
import com.network.monitor.service.AttackStoreService;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class DefenseLogServiceImpl implements DefenseLogService {

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AttackEventService attackEventService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private AttackStoreService attackStoreService;

    @Override
    public void receiveDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }

        try {
            log.info("接收防御日志：defenseType={}, defenseTarget={}, defenseAction={}, eventId={}", 
                logDTO.getDefenseType(), logDTO.getDefenseTarget(), logDTO.getDefenseAction(), logDTO.getEventId());
            
            DefenseLogEntity entity = convertToEntity(logDTO);
            
            defenseLogMapper.insert(entity);
            
            log.info("接收并保存防御日志成功：defenseType={}, defenseTarget={}, defenseAction={}, executeStatus={}, isFirst={}, eventId={}", 
                logDTO.getDefenseType(), logDTO.getDefenseTarget(), logDTO.getDefenseAction(), logDTO.getExecuteStatus(), entity.getIsFirst(), entity.getEventId());

            if (logDTO.getExecuteStatus() != null && logDTO.getExecuteStatus() == 1) {
                if (entity.getIsFirst() == 1) {
                    publishBlacklistSyncEvent(logDTO);
                }
                triggerAlert(logDTO, entity);
            } else if (entity.getIsFirst() == 0) {
                log.debug("非首次防御日志，跳过黑名单事件发布：eventId={}", entity.getEventId());
            }
            
        } catch (Exception e) {
            log.error("接收防御日志失败：defenseType={}, defenseTarget={}, eventId={}", 
                logDTO.getDefenseType(), logDTO.getDefenseTarget(), logDTO.getEventId(), e);
        }
    }

    private void triggerAlert(DefenseLogDTO logDTO, DefenseLogEntity entity) {
        try {
            Long attackId = entity.getAttackId();
            String eventId = entity.getEventId();
            String sourceIp = logDTO.getDefenseTarget();
            String attackType = logDTO.getAttackType();
            String riskLevel = logDTO.getRiskLevel();
            
            if (attackType == null || attackType.isEmpty()) {
                attackType = "UNKNOWN";
            }
            if (riskLevel == null || riskLevel.isEmpty()) {
                riskLevel = "MEDIUM";
            }
            
            if (eventId != null && !eventId.isEmpty()) {
                AttackEventEntity event = attackEventService.getEventByEventId(eventId);
                if (event != null) {
                    if (event.getAttackType() != null && !event.getAttackType().isEmpty()) {
                        attackType = event.getAttackType();
                    }
                    if (event.getRiskLevel() != null && !event.getRiskLevel().isEmpty()) {
                        riskLevel = event.getRiskLevel();
                    }
                }
                
                if (attackId == null) {
                    List<AttackMonitorEntity> attacks = attackMonitorMapper.selectByCondition(
                        eventId, null, null, null, null, null, null, 0, 1, "id DESC");
                    if (attacks != null && !attacks.isEmpty()) {
                        attackId = attacks.get(0).getId();
                        log.debug("根据eventId查找到攻击记录: eventId={}, attackId={}", eventId, attackId);
                    }
                }
            }
            
            if (attackId == null && sourceIp != null && !sourceIp.isEmpty()) {
                AttackMonitorEntity newAttack = new AttackMonitorEntity();
                newAttack.setEventId(eventId);
                newAttack.setSourceIp(sourceIp);
                newAttack.setAttackType(attackType);
                newAttack.setRiskLevel(riskLevel);
                newAttack.setAttackContent(logDTO.getDefenseReason());
                newAttack.setHandled(0);
                newAttack.setCreateTime(LocalDateTime.now());
                newAttack.setUpdateTime(LocalDateTime.now());
                
                attackId = attackStoreService.saveAttack(newAttack);
                if (attackId != null) {
                    log.info("创建攻击记录: attackId={}, eventId={}, sourceIp={}, attackType={}", 
                        attackId, eventId, sourceIp, attackType);
                }
            }
            
            AlertDTO alertDTO = AlertDTO.fromAttack(attackId, eventId, sourceIp, attackType, riskLevel);
            alertService.generateAlert(alertDTO);
            
            log.info("触发告警生成：attackId={}, eventId={}, sourceIp={}, attackType={}, riskLevel={}", 
                attackId, eventId, sourceIp, attackType, riskLevel);
        } catch (Exception e) {
            log.error("触发告警失败：eventId={}, sourceIp={}", entity.getEventId(), logDTO.getDefenseTarget(), e);
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
        
        if (entity.getEventId() != null && !entity.getEventId().isEmpty()) {
            int existingCount = defenseLogMapper.countByEventId(entity.getEventId());
            entity.setIsFirst(existingCount == 0 ? 1 : 0);
            
            if (isBlacklistAction && existingCount > 0) {
                boolean hasBlacklistRecord = hasBlacklistRecordForEvent(entity.getEventId());
                if (hasBlacklistRecord) {
                    entity.setDefenseAction("ADD_BLACKLIST_REPEAT");
                    log.info("该事件已有黑名单记录，设置defenseAction=ADD_BLACKLIST_REPEAT: eventId={}, ip={}", 
                        entity.getEventId(), dto.getDefenseTarget());
                }
            }
        } else {
            entity.setIsFirst(0);
        }
        
        LocalDateTime now = LocalDateTime.now();
        entity.setExecuteTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        
        return entity;
    }
    
    private boolean hasBlacklistRecordForEvent(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return false;
        }
        List<DefenseLogEntity> logs = defenseLogMapper.selectByCondition(
            eventId, null, null, null, null, null, 0, Integer.MAX_VALUE, null, null
        );
        return logs.stream().anyMatch(log -> 
            "ADD_BLACKLIST".equals(log.getDefenseAction()) || 
            "BLACKLIST".equals(log.getDefenseAction()) ||
            "BLOCK_IP".equals(log.getDefenseType()) ||
            "ADD_BLACKLIST_REPEAT".equals(log.getDefenseAction())
        );
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
