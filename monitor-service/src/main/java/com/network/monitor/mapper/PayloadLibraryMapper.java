package com.network.monitor.mapper;

import com.network.monitor.entity.PayloadLibraryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Payload库 Mapper 接口
 */
@Mapper
public interface PayloadLibraryMapper {

    /**
     * 插入Payload
     */
    int insert(PayloadLibraryEntity entity);

    /**
     * 根据 ID 查询Payload
     */
    PayloadLibraryEntity selectById(@Param("id") Long id);

    /**
     * 根据漏洞类型查询Payload
     */
    List<PayloadLibraryEntity> selectByVulnType(@Param("vulnType") String vulnType);

    /**
     * 根据漏洞类型和级别查询Payload
     */
    List<PayloadLibraryEntity> selectByVulnTypeAndLevel(
            @Param("vulnType") String vulnType,
            @Param("payloadLevel") String payloadLevel);

    /**
     * 查询所有启用的Payload
     */
    List<PayloadLibraryEntity> selectAllEnabled();

    /**
     * 查询所有Payload
     */
    List<PayloadLibraryEntity> selectAll();

    /**
     * 根据条件分页查询Payload
     */
    List<PayloadLibraryEntity> selectByCondition(
            @Param("vulnType") String vulnType,
            @Param("payloadLevel") String payloadLevel,
            @Param("description") String description,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 根据条件统计Payload数量
     */
    long countByCondition(
            @Param("vulnType") String vulnType,
            @Param("payloadLevel") String payloadLevel,
            @Param("description") String description,
            @Param("enabled") Integer enabled);

    /**
     * 更新Payload
     */
    int update(PayloadLibraryEntity entity);

    /**
     * 删除Payload
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新Payload启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
