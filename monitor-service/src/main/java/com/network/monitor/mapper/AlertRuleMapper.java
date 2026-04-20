package com.network.monitor.mapper;

import com.network.monitor.entity.AlertRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警规则 Mapper 接口
 */
@Mapper
public interface AlertRuleMapper {

    int insert(AlertRuleEntity entity);

    int update(AlertRuleEntity entity);

    AlertRuleEntity selectById(@Param("id") Long id);

    AlertRuleEntity selectByRuleCode(@Param("ruleCode") String ruleCode);

    List<AlertRuleEntity> selectAll();

    List<AlertRuleEntity> selectEnabled();

    List<AlertRuleEntity> selectByAttackType(@Param("attackType") String attackType);

    List<AlertRuleEntity> selectByCondition(
            @Param("ruleName") String ruleName,
            @Param("attackType") String attackType,
            @Param("enabled") Integer enabled,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    long countByCondition(
            @Param("ruleName") String ruleName,
            @Param("attackType") String attackType,
            @Param("enabled") Integer enabled
    );

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    int deleteById(@Param("id") Long id);
}
