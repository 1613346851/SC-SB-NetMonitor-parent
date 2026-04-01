package com.network.monitor.mapper;

import com.network.monitor.entity.AlertEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警 Mapper 接口
 */
@Mapper
public interface AlertMapper {

    int insert(AlertEntity entity);

    int update(AlertEntity entity);

    AlertEntity selectById(@Param("id") Long id);

    AlertEntity selectByAlertId(@Param("alertId") String alertId);

    AlertEntity selectByEventId(@Param("eventId") String eventId);

    List<AlertEntity> selectByCondition(
            @Param("alertLevel") String alertLevel,
            @Param("status") Integer status,
            @Param("sourceIp") String sourceIp,
            @Param("attackType") String attackType,
            @Param("isSuppressed") Integer isSuppressed,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("orderBy") String orderBy
    );

    long countByCondition(
            @Param("alertLevel") String alertLevel,
            @Param("status") Integer status,
            @Param("sourceIp") String sourceIp,
            @Param("attackType") String attackType,
            @Param("isSuppressed") Integer isSuppressed,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<AlertEntity> selectPendingAlerts(@Param("limit") Integer limit);

    List<AlertEntity> selectUnnotifiedAlerts(@Param("limit") Integer limit);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateNotifyStatus(@Param("id") Long id, @Param("notifyStatus") Integer notifyStatus, @Param("notifyTime") LocalDateTime notifyTime);

    int confirm(@Param("id") Long id, @Param("confirmBy") String confirmBy, @Param("confirmTime") LocalDateTime confirmTime);

    int ignore(@Param("id") Long id, @Param("ignoreBy") String ignoreBy, @Param("ignoreReason") String ignoreReason, @Param("ignoreTime") LocalDateTime ignoreTime);

    int batchConfirm(@Param("ids") List<Long> ids, @Param("confirmBy") String confirmBy, @Param("confirmTime") LocalDateTime confirmTime);

    int setSuppressed(@Param("id") Long id, @Param("suppressUntil") LocalDateTime suppressUntil);

    List<LevelCountStat> countByAlertLevel(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<StatusCountStat> countByStatus();

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    class LevelCountStat {
        private String alertLevel;
        private Long count;

        public String getAlertLevel() {
            return alertLevel;
        }

        public void setAlertLevel(String alertLevel) {
            this.alertLevel = alertLevel;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    class StatusCountStat {
        private Integer status;
        private Long count;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }
}
