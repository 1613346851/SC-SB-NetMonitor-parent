package com.network.monitor.service;

import com.network.monitor.entity.UserEntity;

import java.util.List;

public interface UserService {
    
    UserEntity getByUsername(String username);
    
    UserEntity getById(Long id);
    
    List<UserEntity> listUsers(String username, Integer status);
    
    List<UserEntity> listUsers(String username, Integer status, Integer offset, Integer limit);
    
    long countUsers(String username, Integer status);
    
    boolean createUser(UserEntity user, List<Long> roleIds);
    
    boolean updateUser(UserEntity user, List<Long> roleIds);
    
    boolean deleteUser(Long id);
    
    boolean updateStatus(Long id, Integer status);
    
    boolean resetPassword(Long id, String newPassword);
    
    boolean updateLoginInfo(Long id, String loginIp);
    
    boolean incrementLoginFailCount(Long id);
    
    boolean resetLoginFailCount(Long id);
    
    boolean checkUsernameExists(String username);
    
    List<Long> getUserRoleIds(Long userId);
    
    boolean assignRoles(Long userId, List<Long> roleIds);
}
