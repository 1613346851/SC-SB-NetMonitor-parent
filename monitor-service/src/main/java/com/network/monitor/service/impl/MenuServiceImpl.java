package com.network.monitor.service.impl;

import com.network.monitor.entity.MenuEntity;
import com.network.monitor.mapper.MenuMapper;
import com.network.monitor.mapper.RoleMenuMapper;
import com.network.monitor.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MenuServiceImpl implements MenuService {
    
    @Autowired
    private MenuMapper menuMapper;
    
    @Autowired
    private RoleMenuMapper roleMenuMapper;
    
    @Override
    public MenuEntity getById(Long id) {
        return menuMapper.selectById(id);
    }
    
    @Override
    public List<MenuEntity> listMenus(String menuName, Integer status) {
        return menuMapper.selectList(menuName, status, 0);
    }
    
    @Override
    public List<MenuEntity> getAllMenus() {
        return menuMapper.selectAllMenus();
    }
    
    @Override
    public List<MenuEntity> getMenusByUserId(Long userId) {
        return menuMapper.selectByUserId(userId);
    }
    
    @Override
    public List<MenuEntity> getMenusByRoleId(Long roleId) {
        return menuMapper.selectByRoleId(roleId);
    }
    
    @Override
    public List<MenuEntity> getMenuTree() {
        List<MenuEntity> allMenus = menuMapper.selectAllMenus();
        return buildMenuTree(allMenus, 0L);
    }
    
    private List<MenuEntity> buildMenuTree(List<MenuEntity> menus, Long parentId) {
        List<MenuEntity> result = new ArrayList<>();
        
        Map<Long, List<MenuEntity>> menuMap = menus.stream()
                .collect(Collectors.groupingBy(MenuEntity::getParentId));
        
        List<MenuEntity> children = menuMap.get(parentId);
        if (children != null) {
            for (MenuEntity menu : children) {
                menu.setChildren(buildMenuTree(menus, menu.getId()));
                result.add(menu);
            }
        }
        
        return result;
    }
    
    @Override
    public boolean createMenu(MenuEntity menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        menu.setStatus(0);
        menu.setDelFlag(0);
        if (menu.getSortOrder() == null) {
            menu.setSortOrder(0);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(0);
        }
        if (menu.getMenuType() == null) {
            menu.setMenuType(1);
        }
        
        return menuMapper.insert(menu) > 0;
    }
    
    @Override
    public boolean updateMenu(MenuEntity menu) {
        return menuMapper.update(menu) > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteMenu(Long id) {
        if (hasChildren(id)) {
            return false;
        }
        roleMenuMapper.deleteByMenuId(id);
        return menuMapper.deleteById(id) > 0;
    }
    
    @Override
    public boolean hasChildren(Long menuId) {
        return menuMapper.countByParentId(menuId) > 0;
    }
}
