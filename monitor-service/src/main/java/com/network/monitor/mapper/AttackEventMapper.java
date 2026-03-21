package com.network.monitor.mapper;

import com.network.monitor.entity.AttackEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttackEventMapper {

    int insert(AttackEventEntity entity);

    int update(AttackEventEntity entity);

    AttackEventEntity selectById(@Param("id") Long id);

    AttackEventEntity selectByEventId(@Param("eventId") String eventId);

    AttackEventEntity selectOngoingEventByIp(@Param("sourceIp") String sourceIp);

    List<AttackEventEntity> selectByCondition(
            @Param("eventId") String eventId,
            @Param("sourceIp") String sourceIp,
            @Param("attackType") String attackType,
            @Param("riskLevel") String riskLevel,
            @Param("status") Integer status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("orderBy") String orderBy
    );

    long countByCondition(
            @Param("eventId") String eventId,
            @Param("sourceIp") String sourceIp,
            @Param("attackType") String attackType,
            @Param("riskLevel") String riskLevel,
            @Param("status") Integer status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int incrementAttackCount(@Param("id") Long id);

    int updateStatistics(
            @Param("id") Long id,
            @Param("totalRequests") Integer totalRequests,
            @Param("peakRps") Integer peakRps,
            @Param("confidenceEnd") Integer confidenceEnd
    );

    int markAsEnded(
            @Param("id") Long id,
            @Param("endTime") LocalDateTime endTime,
            @Param("durationSeconds") Integer durationSeconds
    );

    List<EventTypeStat> countByAttackType();

    class EventTypeStat {
        private String attackType;
        private Long count;

        public String getAttackType() {
            return attackType;
        }

        public void setAttackType(String attackType) {
            this.attackType = attackType;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    List<EventRiskStat> countByRiskLevel();

    class EventRiskStat {
        private String riskLevel;
        private Long count;

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    List<EventTrendStat> countEventTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    class EventTrendStat {
        private LocalDateTime time;
        private Long count;

        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    List<AttackEventEntity> selectRecentEvents(@Param("limit") int limit);

    int countOngoingEvents();

    int countEventsByIp(@Param("sourceIp") String sourceIp);
}
