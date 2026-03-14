package com.network.monitor.service;

import com.network.monitor.entity.RoleEntity;

import java.util.List;

public interface RoleService {
    
    RoleEntity getById(Long id);
    
    RoleEntity getByCode(String roleCode);
    
    List<RoleEntity> listRoles(String roleName, Integer status);
    
    List<RoleEntity> getRolesByUserId(Long userId);
    
    boolean createRole(RoleEntity role, List<Long> menuIds);
    
    boolean updateRole(RoleEntity role, List<Long> menuIds);
    
    boolean deleteRole(Long id);
    
    boolean checkRoleCodeExists(String roleCode);
    
    List<Long> getRoleMenuIds(Long roleId);
    
    boolean assignMenus(Long roleId, List<Long> menuIds);
}
