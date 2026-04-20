package com.network.monitor.mapper;

import com.network.monitor.entity.InterfaceRuleEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 接口-规则关联 Mapper
 */
@Mapper
public interface InterfaceRuleMapper {

    @Select("SELECT * FROM sys_interface_rule WHERE interface_id = #{interfaceId}")
    List<InterfaceRuleEntity> selectByInterfaceId(Long interfaceId);

    @Select("SELECT * FROM sys_interface_rule WHERE rule_id = #{ruleId}")
    List<InterfaceRuleEntity> selectByRuleId(Long ruleId);

    @Select("SELECT COUNT(*) FROM sys_interface_rule WHERE interface_id = #{interfaceId}")
    int countByInterfaceId(Long interfaceId);

    @Select("SELECT COUNT(*) FROM sys_interface_rule WHERE rule_id = #{ruleId}")
    int countByRuleId(Long ruleId);

    @Insert("INSERT INTO sys_interface_rule (interface_id, rule_id, rule_name, attack_type, risk_level, create_time) " +
            "VALUES (#{interfaceId}, #{ruleId}, #{ruleName}, #{attackType}, #{riskLevel}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InterfaceRuleEntity entity);

    @Delete("DELETE FROM sys_interface_rule WHERE interface_id = #{interfaceId} AND rule_id = #{ruleId}")
    int deleteByInterfaceIdAndRuleId(@Param("interfaceId") Long interfaceId, @Param("ruleId") Long ruleId);

    @Delete("DELETE FROM sys_interface_rule WHERE interface_id = #{interfaceId}")
    int deleteByInterfaceId(Long interfaceId);

    @Delete("DELETE FROM sys_interface_rule WHERE rule_id = #{ruleId}")
    int deleteByRuleId(Long ruleId);

    @Select("SELECT DISTINCT interface_id FROM sys_interface_rule WHERE rule_id = #{ruleId}")
    List<Long> selectInterfaceIdsByRuleId(Long ruleId);
}
