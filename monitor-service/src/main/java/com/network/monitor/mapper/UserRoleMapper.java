package com.network.monitor.mapper;

import com.network.monitor.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRoleMapper {
    
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
    
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
    
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
    
    int deleteByUserId(@Param("userId") Long userId);
    
    int deleteByRoleId(@Param("roleId") Long roleId);
    
    int deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
