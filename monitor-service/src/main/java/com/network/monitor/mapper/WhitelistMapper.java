package com.network.monitor.mapper;

import com.network.monitor.entity.WhitelistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 检测白名单 Mapper 接口
 */
@Mapper
public interface WhitelistMapper {

    /**
     * 插入白名单
     */
    int insert(WhitelistEntity entity);

    /**
     * 根据 ID 查询白名单
     */
    WhitelistEntity selectById(@Param("id") Long id);

    /**
     * 查询所有启用的白名单
     */
    List<WhitelistEntity> selectAllEnabled();

    /**
     * 查询所有白名单
     */
    List<WhitelistEntity> selectAll();

    /**
     * 根据类型查询白名单
     */
    List<WhitelistEntity> selectByType(@Param("whitelistType") String whitelistType);

    /**
     * 根据条件分页查询白名单
     */
    List<WhitelistEntity> selectByCondition(
            @Param("whitelistType") String whitelistType,
            @Param("whitelistValue") String whitelistValue,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 根据条件分页查询白名单（支持排序）
     */
    List<WhitelistEntity> selectByConditionWithSort(
            @Param("whitelistType") String whitelistType,
            @Param("whitelistValue") String whitelistValue,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("sortField") String sortField,
            @Param("sortOrder") String sortOrder);

    /**
     * 根据条件统计白名单数量
     */
    long countByCondition(
            @Param("whitelistType") String whitelistType,
            @Param("whitelistValue") String whitelistValue,
            @Param("enabled") Integer enabled);

    /**
     * 更新白名单
     */
    int update(WhitelistEntity entity);

    /**
     * 删除白名单
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新白名单启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
