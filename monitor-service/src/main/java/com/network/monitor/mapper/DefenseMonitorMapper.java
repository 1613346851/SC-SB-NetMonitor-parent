package com.network.monitor.mapper;

import com.network.monitor.entity.DefenseMonitorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 防御日志 Mapper 接口
 */
@Mapper
public interface DefenseMonitorMapper {

    /**
     * 插入防御日志
     */
    int insert(DefenseMonitorEntity entity);

    /**
     * 根据 ID 查询防御日志
     */
    DefenseMonitorEntity selectById(@Param("id") Long id);

    /**
     * 根据 ID 更新防御日志
     */
    int updateById(DefenseMonitorEntity entity);

    /**
     * 分页查询防御日志
     */
    List<DefenseMonitorEntity> selectByCondition(
            @Param("defenseType") String defenseType,
            @Param("attackId") Long attackId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 统计总记录数
     */
    long countByCondition(
            @Param("defenseType") String defenseType,
            @Param("attackId") Long attackId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 批量插入防御日志
     */
    int batchInsert(@Param("list") List<DefenseMonitorEntity> list);

    /**
     * 按时间统计防御趋势
     */
    List<TrendStat> countDefenseTrend(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询所有有效的黑名单记录
     */
    List<DefenseMonitorEntity> selectValidBlacklists();

    /**
     * 查询所有黑名单记录（包括已过期的）
     */
    List<DefenseMonitorEntity> selectAllBlacklists();

    /**
     * 查询指定IP的所有黑名单记录
     */
    List<DefenseMonitorEntity> selectBlacklistsByIp(@Param("ip") String ip);

    /**
     * 删除指定IP的所有黑名单记录
     */
    int deleteAllBlacklistsByIp(@Param("ip") String ip);

    /**
     * 防御趋势统计内部类
     */
    class TrendStat {
        private LocalDateTime time;
        private Long count;

        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }
}
