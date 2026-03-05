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
}
