package com.network.monitor.mapper;

import com.network.monitor.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {
    
    RoleEntity selectById(@Param("id") Long id);
    
    RoleEntity selectByCode(@Param("roleCode") String roleCode);
    
    List<RoleEntity> selectList(@Param("roleName") String roleName,
                                 @Param("status") Integer status,
                                 @Param("delFlag") Integer delFlag);
    
    List<RoleEntity> selectListPaged(@Param("roleName") String roleName,
                                      @Param("status") Integer status,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);
    
    long countList(@Param("roleName") String roleName,
                   @Param("status") Integer status);
    
    List<RoleEntity> selectByUserId(@Param("userId") Long userId);
    
    int insert(RoleEntity entity);
    
    int update(RoleEntity entity);
    
    int deleteById(@Param("id") Long id);
    
    int countByRoleCode(@Param("roleCode") String roleCode);
}
