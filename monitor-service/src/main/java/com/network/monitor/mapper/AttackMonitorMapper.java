package com.network.monitor.mapper;

import com.network.monitor.entity.AttackMonitorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 攻击监测 Mapper 接口
 */
@Mapper
public interface AttackMonitorMapper {

    /**
     * 插入攻击记录
     */
    int insert(AttackMonitorEntity entity);

    /**
     * 根据 ID 查询攻击记录
     */
    AttackMonitorEntity selectById(@Param("id") Long id);

    /**
     * 分页查询攻击记录
     */
    List<AttackMonitorEntity> selectByCondition(
            @Param("eventId") String eventId,
            @Param("attackType") String attackType,
            @Param("riskLevel") String riskLevel,
            @Param("sourceIp") String sourceIp,
            @Param("handled") Integer handled,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("orderBy") String orderBy
    );

    long countByCondition(
            @Param("eventId") String eventId,
            @Param("attackType") String attackType,
            @Param("riskLevel") String riskLevel,
            @Param("sourceIp") String sourceIp,
            @Param("handled") Integer handled,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 更新攻击记录处理状态
     */
    int updateHandled(@Param("id") Long id, @Param("handled") Integer handled, @Param("handleRemark") String handleRemark);

    /**
     * 查询未处理的高危攻击记录
     */
    List<AttackMonitorEntity> selectUnHandledHighRisk();

    /**
     * 统计各攻击类型的数量
     */
    List<AttackTypeStat> countByAttackType();

    /**
     * 攻击类型统计实体
     */
    class AttackTypeStat {
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

    /**
     * 统计各风险等级的数量
     */
    List<RiskLevelStat> countByRiskLevel();

    /**
     * 风险等级统计实体
     */
    class RiskLevelStat {
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

    /**
     * 统计攻击源 IP Top N
     */
    List<SourceIpStat> countTopSourceIps(@Param("limit") int limit);

    /**
     * 攻击源 IP 统计实体
     */
    class SourceIpStat {
        private String sourceIp;
        private Long count;

        public String getSourceIp() {
            return sourceIp;
        }

        public void setSourceIp(String sourceIp) {
            this.sourceIp = sourceIp;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 统计目标 URI Top N
     */
    List<TargetUriStat> countTopTargetUris(@Param("limit") int limit);

    /**
     * 目标 URI 统计实体
     */
    class TargetUriStat {
        private String targetUri;
        private Long count;

        public String getTargetUri() {
            return targetUri;
        }

        public void setTargetUri(String targetUri) {
            this.targetUri = targetUri;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 统计攻击趋势（按小时）
     */
    List<TrendStat> countAttackTrend(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 根据源IP精确查询攻击记录（用于IP画像）
     */
    List<AttackMonitorEntity> selectBySourceIp(
            @Param("sourceIp") String sourceIp,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("orderBy") String orderBy
    );

    /**
     * 根据源IP精确统计攻击数量
     */
    long countBySourceIp(
            @Param("sourceIp") String sourceIp,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 攻击趋势统计实体
     */
    class TrendStat {
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

    /**
     * 更新攻击记录的事件ID
     */
    int updateEventId(@Param("id") Long id, @Param("eventId") String eventId);
    
    /**
     * 根据事件ID更新攻击记录的流量ID
     */
    int updateTrafficIdByEventId(@Param("eventId") String eventId, @Param("trafficId") Long trafficId);
}
