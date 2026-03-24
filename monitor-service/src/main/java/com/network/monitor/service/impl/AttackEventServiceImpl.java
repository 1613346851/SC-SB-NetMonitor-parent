package com.network.monitor.service.impl;

import com.network.monitor.common.util.IpNormalizeUtil;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.mapper.AttackEventMapper;
import com.network.monitor.service.AttackEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AttackEventServiceImpl implements AttackEventService {

    @Autowired
    private AttackEventMapper attackEventMapper;

    @Override
    @Transactional
    public AttackEventEntity createEvent(AttackMonitorDTO attackDTO) {
        if (attackDTO == null) {
            return null;
        }

        String normalizedIp = IpNormalizeUtil.normalize(attackDTO.getSourceIp());
        String eventId = generateEventId();

        AttackEventEntity entity = new AttackEventEntity();
        entity.setEventId(eventId);
        entity.setSourceIp(normalizedIp);
        entity.setAttackType(attackDTO.getAttackType());
        entity.setRiskLevel(attackDTO.getRiskLevel());
        entity.setStartTime(LocalDateTime.now());
        entity.setTotalRequests(1);
        entity.setAttackCount(1);
        entity.setConfidenceStart(attackDTO.getConfidence());
        entity.setConfidenceEnd(attackDTO.getConfidence());
        entity.setStatus(AttackEventEntity.STATUS_ONGOING);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        attackEventMapper.insert(entity);

        log.info("创建攻击事件：eventId={}, sourceIp={}, attackType={}, riskLevel={}", 
            eventId, normalizedIp, attackDTO.getAttackType(), attackDTO.getRiskLevel());

        return entity;
    }

    @Override
    @Transactional
    public AttackEventEntity getOrCreateEvent(String sourceIp, String attackType, String riskLevel, int confidence) {
        String normalizedIp = IpNormalizeUtil.normalize(sourceIp);

        AttackEventEntity existingEvent = attackEventMapper.selectOngoingEventByIp(normalizedIp);
        if (existingEvent != null) {
            log.debug("IP已有进行中的事件：eventId={}, ip={}", existingEvent.getEventId(), normalizedIp);
            return existingEvent;
        }

        String eventId = generateEventId();

        AttackEventEntity entity = new AttackEventEntity();
        entity.setEventId(eventId);
        entity.setSourceIp(normalizedIp);
        entity.setAttackType(attackType);
        entity.setRiskLevel(riskLevel);
        entity.setStartTime(LocalDateTime.now());
        entity.setTotalRequests(1);
        entity.setAttackCount(1);
        entity.setConfidenceStart(confidence);
        entity.setConfidenceEnd(confidence);
        entity.setStatus(AttackEventEntity.STATUS_ONGOING);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        attackEventMapper.insert(entity);

        log.info("创建新攻击事件：eventId={}, sourceIp={}, attackType={}", eventId, normalizedIp, attackType);

        return entity;
    }

    @Override
    public AttackEventEntity getEventById(Long id) {
        if (id == null) {
            return null;
        }
        return attackEventMapper.selectById(id);
    }

    @Override
    public AttackEventEntity getEventByEventId(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return null;
        }
        return attackEventMapper.selectByEventId(eventId);
    }

    @Override
    public AttackEventEntity getOngoingEventByIp(String sourceIp) {
        if (sourceIp == null || sourceIp.isEmpty()) {
            return null;
        }
        String normalizedIp = IpNormalizeUtil.normalize(sourceIp);
        return attackEventMapper.selectOngoingEventByIp(normalizedIp);
    }

    @Override
    @Transactional
    public void updateEventStatistics(Long eventId, int totalRequests, int peakRps, int confidence) {
        if (eventId == null) {
            return;
        }
        attackEventMapper.updateStatistics(eventId, totalRequests, peakRps, confidence);
        log.debug("更新事件统计：eventId={}, totalRequests={}, peakRps={}, confidence={}", 
            eventId, totalRequests, peakRps, confidence);
    }

    @Override
    @Transactional
    public void incrementAttackCount(Long eventId) {
        if (eventId == null) {
            return;
        }
        attackEventMapper.incrementAttackCount(eventId);
    }

    @Override
    @Transactional
    public void markEventAsEnded(Long eventId) {
        if (eventId == null) {
            return;
        }

        AttackEventEntity entity = attackEventMapper.selectById(eventId);
        if (entity == null) {
            log.warn("事件不存在，无法标记结束：eventId={}", eventId);
            return;
        }

        LocalDateTime endTime = LocalDateTime.now();
        int durationSeconds = 0;
        if (entity.getStartTime() != null) {
            durationSeconds = (int) java.time.Duration.between(entity.getStartTime(), endTime).getSeconds();
        }

        attackEventMapper.markAsEnded(eventId, endTime, durationSeconds);

        log.info("事件已结束：eventId={}, duration={}秒", eventId, durationSeconds);
    }

    @Override
    @Transactional
    public void setDefenseInfo(Long eventId, String defenseAction, LocalDateTime expireTime, boolean success) {
        if (eventId == null) {
            return;
        }

        AttackEventEntity entity = attackEventMapper.selectById(eventId);
        if (entity == null) {
            log.warn("事件不存在，无法设置防御信息：eventId={}", eventId);
            return;
        }

        entity.setDefenseAction(defenseAction);
        entity.setDefenseExpireTime(expireTime);
        entity.setDefenseSuccess(success ? 1 : 0);
        entity.setUpdateTime(LocalDateTime.now());

        attackEventMapper.update(entity);

        log.info("设置事件防御信息：eventId={}, action={}, success={}", eventId, defenseAction, success);
    }

    @Override
    public List<AttackEventEntity> getEventsByCondition(
            String eventId, String sourceIp, String attackType, String riskLevel,
            Integer status, LocalDateTime startTime, LocalDateTime endTime,
            int page, int pageSize, String orderBy) {

        int offset = (page - 1) * pageSize;
        return attackEventMapper.selectByCondition(
            eventId, sourceIp, attackType, riskLevel, status, startTime, endTime, 
            offset, pageSize, orderBy
        );
    }

    @Override
    public long countEventsByCondition(
            String eventId, String sourceIp, String attackType, String riskLevel,
            Integer status, LocalDateTime startTime, LocalDateTime endTime) {

        return attackEventMapper.countByCondition(
            eventId, sourceIp, attackType, riskLevel, status, startTime, endTime
        );
    }

    @Override
    public List<AttackEventEntity> getRecentEvents(int limit) {
        return attackEventMapper.selectRecentEvents(limit);
    }

    @Override
    public int getOngoingEventCount() {
        return attackEventMapper.countOngoingEvents();
    }

    @Override
    public Map<String, Object> getEventStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<AttackEventMapper.EventTypeStat> typeStats = attackEventMapper.countByAttackType();
        Map<String, Long> typeCount = new HashMap<>();
        for (AttackEventMapper.EventTypeStat stat : typeStats) {
            typeCount.put(stat.getAttackType(), stat.getCount());
        }
        stats.put("attackTypeStats", typeCount);

        List<AttackEventMapper.EventRiskStat> riskStats = attackEventMapper.countByRiskLevel();
        Map<String, Long> riskCount = new HashMap<>();
        for (AttackEventMapper.EventRiskStat stat : riskStats) {
            riskCount.put(stat.getRiskLevel(), stat.getCount());
        }
        stats.put("riskLevelStats", riskCount);

        int ongoingCount = attackEventMapper.countOngoingEvents();
        long totalCount = attackEventMapper.countByCondition(null, null, null, null, null, null, null);
        
        stats.put("ongoingCount", ongoingCount);
        stats.put("totalEvents", totalCount);
        stats.put("ongoingEvents", ongoingCount);
        stats.put("endedEvents", totalCount - ongoingCount);
        
        Double avgDuration = attackEventMapper.selectAvgDuration();
        stats.put("avgDuration", avgDuration != null ? avgDuration.intValue() : 0);

        return stats;
    }

    @Override
    public List<AttackEventEntity> getEventsByIp(String sourceIp) {
        if (sourceIp == null || sourceIp.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedIp = IpNormalizeUtil.normalize(sourceIp);
        return attackEventMapper.selectByCondition(
            null, normalizedIp, null, null, null, null, null, 
            0, 100, "start_time DESC"
        );
    }

    private String generateEventId() {
        return "EVT_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    @Transactional
    public int endExpiredEvents(int expireMinutes) {
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(expireMinutes);
        List<AttackEventEntity> expiredEvents = attackEventMapper.selectOngoingEventsNotUpdatedSince(thresholdTime);
        
        int endedCount = 0;
        for (AttackEventEntity event : expiredEvents) {
            try {
                LocalDateTime endTime = LocalDateTime.now();
                int durationSeconds = 0;
                if (event.getStartTime() != null) {
                    durationSeconds = (int) java.time.Duration.between(event.getStartTime(), endTime).getSeconds();
                }
                
                attackEventMapper.markAsEnded(event.getId(), endTime, durationSeconds);
                endedCount++;
                
                log.info("自动结束过期事件：eventId={}, ip={}, duration={}秒", 
                    event.getEventId(), event.getSourceIp(), durationSeconds);
            } catch (Exception e) {
                log.error("结束过期事件失败：eventId={}", event.getEventId(), e);
            }
        }
        
        if (endedCount > 0) {
            log.info("批量结束过期事件完成：共{}个事件已结束", endedCount);
        }
        
        return endedCount;
    }
}
