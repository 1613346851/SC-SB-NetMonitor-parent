package com.network.monitor.mapper;

import com.network.monitor.entity.MenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MenuMapper {
    
    MenuEntity selectById(@Param("id") Long id);
    
    List<MenuEntity> selectList(@Param("menuName") String menuName,
                                 @Param("status") Integer status,
                                 @Param("delFlag") Integer delFlag);
    
    List<MenuEntity> selectByParentId(@Param("parentId") Long parentId);
    
    List<MenuEntity> selectByUserId(@Param("userId") Long userId);
    
    List<MenuEntity> selectByRoleId(@Param("roleId") Long roleId);
    
    List<MenuEntity> selectAllMenus();
    
    int insert(MenuEntity entity);
    
    int update(MenuEntity entity);
    
    int deleteById(@Param("id") Long id);
    
    int countByParentId(@Param("parentId") Long parentId);
}
