package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.entity.AttackEventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AttackEventService {

    AttackEventEntity createEvent(AttackMonitorDTO attackDTO);

    AttackEventEntity getOrCreateEvent(String sourceIp, String attackType, String riskLevel, int confidence);

    AttackEventEntity getOrCreateEventWithEventId(String sourceIp, String attackType, String riskLevel, int confidence, String eventId);

    AttackEventEntity getEventById(Long id);

    AttackEventEntity getEventByEventId(String eventId);

    AttackEventEntity getOngoingEventByIp(String sourceIp);

    void updateEventStatistics(Long eventId, int totalRequests, int peakRps, int confidence);

    void incrementAttackCount(Long eventId);

    void addTotalRequests(String eventId, int count);

    void markEventAsEnded(Long eventId);

    void setDefenseInfo(Long eventId, String defenseAction, LocalDateTime expireTime, boolean success);

    List<AttackEventEntity> getEventsByCondition(
            String eventId, String sourceIp, String attackType, String riskLevel,
            Integer status, LocalDateTime startTime, LocalDateTime endTime,
            int page, int pageSize, String orderBy
    );

    long countEventsByCondition(
            String eventId, String sourceIp, String attackType, String riskLevel,
            Integer status, LocalDateTime startTime, LocalDateTime endTime
    );

    List<AttackEventEntity> getRecentEvents(int limit);

    int getOngoingEventCount();

    Map<String, Object> getEventStatistics();

    List<AttackEventEntity> getEventsByIp(String sourceIp);

    int endExpiredEvents(int expireMinutes);
}
