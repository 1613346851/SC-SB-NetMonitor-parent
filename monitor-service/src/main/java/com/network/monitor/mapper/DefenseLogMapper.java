package com.network.monitor.mapper;

import com.network.monitor.entity.DefenseLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DefenseLogMapper {

    int insert(DefenseLogEntity entity);

    DefenseLogEntity selectById(@Param("id") Long id);

    List<DefenseLogEntity> selectByCondition(
            @Param("eventId") String eventId,
            @Param("defenseType") String defenseType,
            @Param("defenseTarget") String defenseTarget,
            @Param("executeStatus") Integer executeStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    long countByCondition(
            @Param("eventId") String eventId,
            @Param("defenseType") String defenseType,
            @Param("defenseTarget") String defenseTarget,
            @Param("executeStatus") Integer executeStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    int deleteById(@Param("id") Long id);

    long countAll();

    List<DefenseLogEntity> selectValidBlacklists();

    List<DefenseLogEntity> selectAllBlacklists();

    List<DefenseLogEntity> selectBlacklistsByIp(@Param("ip") String ip);

    int deleteAllBlacklistsByIp(@Param("ip") String ip);

    List<TrendStat> countDefenseTrend(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    int countByEventId(@Param("eventId") String eventId);

    class TrendStat {
        private LocalDateTime time;
        private Long count;

        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }
}
