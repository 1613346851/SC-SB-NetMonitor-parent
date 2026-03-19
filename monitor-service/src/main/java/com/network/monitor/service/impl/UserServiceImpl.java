package com.network.monitor.service.impl;

import com.network.monitor.entity.UserEntity;
import com.network.monitor.mapper.UserMapper;
import com.network.monitor.mapper.UserRoleMapper;
import com.network.monitor.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserRoleMapper userRoleMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public UserEntity getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }
    
    @Override
    public UserEntity getById(Long id) {
        return userMapper.selectById(id);
    }
    
    @Override
    public List<UserEntity> listUsers(String username, Integer status) {
        return userMapper.selectList(username, status, 0);
    }
    
    @Override
    @Transactional
    public boolean createUser(UserEntity user, List<Long> roleIds) {
        if (checkUsernameExists(user.getUsername())) {
            return false;
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(0);
        user.setLoginFailCount(0);
        user.setDelFlag(0);
        
        int result = userMapper.insert(user);
        if (result > 0 && roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                userRoleMapper.insert(user.getId(), roleId);
            }
        }
        
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean updateUser(UserEntity user, List<Long> roleIds) {
        UserEntity existing = userMapper.selectById(user.getId());
        if (existing == null) {
            return false;
        }
        
        int result = userMapper.update(user);
        if (result > 0 && roleIds != null) {
            userRoleMapper.deleteByUserId(user.getId());
            for (Long roleId : roleIds) {
                userRoleMapper.insert(user.getId(), roleId);
            }
        }
        
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean deleteUser(Long id) {
        userRoleMapper.deleteByUserId(id);
        return userMapper.deleteById(id) > 0;
    }
    
    @Override
    public boolean updateStatus(Long id, Integer status) {
        return userMapper.updateStatus(id, status) > 0;
    }
    
    @Override
    public boolean resetPassword(Long id, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        return userMapper.updatePassword(id, encodedPassword, LocalDateTime.now()) > 0;
    }
    
    @Override
    public boolean updateLoginInfo(Long id, String loginIp) {
        return userMapper.updateLoginInfo(id, LocalDateTime.now(), loginIp) > 0;
    }
    
    @Override
    public boolean incrementLoginFailCount(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            return false;
        }
        
        int newCount = user.getLoginFailCount() + 1;
        return userMapper.updateLoginFailCount(id, newCount) > 0;
    }
    
    @Override
    public boolean resetLoginFailCount(Long id) {
        return userMapper.resetLoginFailCount(id) > 0;
    }
    
    @Override
    public boolean checkUsernameExists(String username) {
        return userMapper.countByUsername(username) > 0;
    }
    
    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }
    
    @Override
    @Transactional
    public boolean assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                userRoleMapper.insert(userId, roleId);
            }
        }
        return true;
    }
}
