package com.network.monitor.service.impl;

import com.network.monitor.entity.RoleEntity;
import com.network.monitor.mapper.RoleMapper;
import com.network.monitor.mapper.RoleMenuMapper;
import com.network.monitor.mapper.UserRoleMapper;
import com.network.monitor.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {
    
    @Autowired
    private RoleMapper roleMapper;
    
    @Autowired
    private RoleMenuMapper roleMenuMapper;
    
    @Autowired
    private UserRoleMapper userRoleMapper;
    
    @Override
    public RoleEntity getById(Long id) {
        return roleMapper.selectById(id);
    }
    
    @Override
    public RoleEntity getByCode(String roleCode) {
        return roleMapper.selectByCode(roleCode);
    }
    
    @Override
    public List<RoleEntity> listRoles(String roleName, Integer status) {
        return roleMapper.selectList(roleName, status, 0);
    }
    
    @Override
    public List<RoleEntity> getRolesByUserId(Long userId) {
        return roleMapper.selectByUserId(userId);
    }
    
    @Override
    @Transactional
    public boolean createRole(RoleEntity role, List<Long> menuIds) {
        if (checkRoleCodeExists(role.getRoleCode())) {
            return false;
        }
        
        role.setStatus(0);
        role.setDelFlag(0);
        
        int result = roleMapper.insert(role);
        if (result > 0 && menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.batchInsert(role.getId(), menuIds);
        }
        
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean updateRole(RoleEntity role, List<Long> menuIds) {
        RoleEntity existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            return false;
        }
        
        int result = roleMapper.update(role);
        if (result > 0 && menuIds != null) {
            roleMenuMapper.deleteByRoleId(role.getId());
            if (!menuIds.isEmpty()) {
                roleMenuMapper.batchInsert(role.getId(), menuIds);
            }
        }
        
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteRole(Long id) {
        userRoleMapper.deleteByRoleId(id);
        roleMenuMapper.deleteByRoleId(id);
        return roleMapper.deleteById(id) > 0;
    }
    
    @Override
    public boolean checkRoleCodeExists(String roleCode) {
        return roleMapper.countByRoleCode(roleCode) > 0;
    }
    
    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }
    
    @Override
    @Transactional
    public boolean assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.batchInsert(roleId, menuIds);
        }
        return true;
    }
}
