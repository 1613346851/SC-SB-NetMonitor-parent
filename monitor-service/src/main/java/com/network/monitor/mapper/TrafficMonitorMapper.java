package com.network.monitor.mapper;

import com.network.monitor.entity.TrafficMonitorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流量监测 Mapper 接口
 */
@Mapper
public interface TrafficMonitorMapper {

    /**
     * 插入流量记录
     */
    int insert(TrafficMonitorEntity entity);

    /**
     * 根据 ID 查询流量记录
     */
    TrafficMonitorEntity selectById(@Param("id") Long id);

    /**
     * 分页查询流量记录
     */
    List<TrafficMonitorEntity> selectByCondition(
            @Param("sourceIp") String sourceIp,
            @Param("requestUri") String requestUri,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 统计总记录数
     */
    long countByCondition(
            @Param("sourceIp") String sourceIp,
            @Param("requestUri") String requestUri,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 删除流量记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 批量插入流量记录
     */
    int batchInsert(@Param("list") List<TrafficMonitorEntity> list);
}
