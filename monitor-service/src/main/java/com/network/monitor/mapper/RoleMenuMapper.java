package com.network.monitor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMenuMapper {
    
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
    
    List<String> selectPermissionsByRoleId(@Param("roleId") Long roleId);
    
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
    
    int insert(@Param("roleId") Long roleId, @Param("menuId") Long menuId);
    
    int deleteByRoleId(@Param("roleId") Long roleId);
    
    int deleteByMenuId(@Param("menuId") Long menuId);
    
    int batchInsert(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
}
