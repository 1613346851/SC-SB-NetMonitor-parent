package com.network.monitor.mapper;

import com.network.monitor.entity.ScanHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 扫描历史Mapper接口
 */
@Mapper
public interface ScanHistoryMapper {

    int insert(ScanHistoryEntity entity);

    ScanHistoryEntity selectById(@Param("id") Long id);

    ScanHistoryEntity selectByTaskId(@Param("taskId") String taskId);

    List<ScanHistoryEntity> selectRecent(@Param("limit") int limit);

    List<ScanHistoryEntity> selectByCondition(
            @Param("scanType") String scanType,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByCondition(
            @Param("scanType") String scanType,
            @Param("status") String status);

    int update(ScanHistoryEntity entity);

    int deleteById(@Param("id") Long id);
}
