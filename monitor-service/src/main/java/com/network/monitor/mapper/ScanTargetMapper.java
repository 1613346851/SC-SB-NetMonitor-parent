package com.network.monitor.mapper;

import com.network.monitor.entity.ScanTargetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 扫描目标配置 Mapper 接口
 */
@Mapper
public interface ScanTargetMapper {

    /**
     * 插入扫描目标
     */
    int insert(ScanTargetEntity entity);

    /**
     * 根据 ID 查询扫描目标
     */
    ScanTargetEntity selectById(@Param("id") Long id);

    /**
     * 查询所有启用的扫描目标
     */
    List<ScanTargetEntity> selectAllEnabled();

    /**
     * 查询所有扫描目标
     */
    List<ScanTargetEntity> selectAll();

    /**
     * 根据条件分页查询扫描目标
     */
    List<ScanTargetEntity> selectByCondition(
            @Param("targetName") String targetName,
            @Param("targetType") String targetType,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 根据条件统计扫描目标数量
     */
    long countByCondition(
            @Param("targetName") String targetName,
            @Param("targetType") String targetType,
            @Param("enabled") Integer enabled);

    /**
     * 更新扫描目标
     */
    int update(ScanTargetEntity entity);

    /**
     * 删除扫描目标
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新扫描目标启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
