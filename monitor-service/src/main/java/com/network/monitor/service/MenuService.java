package com.network.monitor.service;

import com.network.monitor.entity.MenuEntity;

import java.util.List;

public interface MenuService {
    
    MenuEntity getById(Long id);
    
    List<MenuEntity> listMenus(String menuName, Integer status);
    
    List<MenuEntity> getAllMenus();
    
    List<MenuEntity> getMenusByUserId(Long userId);
    
    List<MenuEntity> getMenusByRoleId(Long roleId);
    
    List<MenuEntity> getMenuTree();
    
    boolean createMenu(MenuEntity menu);
    
    boolean updateMenu(MenuEntity menu);
    
    boolean deleteMenu(Long id);
    
    boolean hasChildren(Long menuId);
}
