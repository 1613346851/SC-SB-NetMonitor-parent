package com.network.monitor.mapper;

import com.network.monitor.entity.MonitorRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 攻击规则 Mapper 接口
 */
@Mapper
public interface MonitorRuleMapper {

    /**
     * 插入规则
     */
    int insert(MonitorRuleEntity entity);

    /**
     * 根据 ID 查询规则
     */
    MonitorRuleEntity selectById(@Param("id") Long id);

    /**
     * 查询所有启用的规则
     */
    List<MonitorRuleEntity> selectAllEnabled();

    /**
     * 查询所有规则
     */
    List<MonitorRuleEntity> selectAll();

    /**
     * 根据攻击类型查询规则
     */
    List<MonitorRuleEntity> selectByAttackType(@Param("attackType") String attackType);

    /**
     * 更新规则
     */
    int update(MonitorRuleEntity entity);

    /**
     * 删除规则
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新规则启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 更新规则命中次数
     */
    int updateHitCount(@Param("id") Long id, @Param("hitCount") Integer hitCount);
}
