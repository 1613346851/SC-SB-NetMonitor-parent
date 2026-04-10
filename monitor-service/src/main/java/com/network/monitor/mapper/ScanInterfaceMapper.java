package com.network.monitor.mapper;

import com.network.monitor.entity.ScanInterfaceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 扫描接口配置 Mapper 接口
 */
@Mapper
public interface ScanInterfaceMapper {

    /**
     * 插入扫描接口
     */
    int insert(ScanInterfaceEntity entity);

    /**
     * 根据 ID 查询扫描接口
     */
    ScanInterfaceEntity selectById(@Param("id") Long id);

    /**
     * 根据目标 ID 查询扫描接口
     */
    List<ScanInterfaceEntity> selectByTargetId(@Param("targetId") Long targetId);

    /**
     * 查询所有启用的扫描接口
     */
    List<ScanInterfaceEntity> selectAllEnabled();

    /**
     * 查询所有扫描接口
     */
    List<ScanInterfaceEntity> selectAll();

    /**
     * 根据漏洞类型查询扫描接口
     */
    List<ScanInterfaceEntity> selectByVulnType(@Param("vulnType") String vulnType);

    /**
     * 根据条件分页查询扫描接口
     */
    List<ScanInterfaceEntity> selectByCondition(
            @Param("targetId") Long targetId,
            @Param("interfaceName") String interfaceName,
            @Param("vulnType") String vulnType,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 根据条件统计扫描接口数量
     */
    long countByCondition(
            @Param("targetId") Long targetId,
            @Param("interfaceName") String interfaceName,
            @Param("vulnType") String vulnType,
            @Param("enabled") Integer enabled);

    /**
     * 更新扫描接口
     */
    int update(ScanInterfaceEntity entity);

    /**
     * 删除扫描接口
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据目标 ID 删除扫描接口
     */
    int deleteByTargetId(@Param("targetId") Long targetId);

    /**
     * 更新扫描接口启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
