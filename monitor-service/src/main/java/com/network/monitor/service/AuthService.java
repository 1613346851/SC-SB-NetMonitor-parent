package com.network.monitor.service;

import com.network.monitor.entity.MenuEntity;
import com.network.monitor.entity.UserEntity;

import java.util.List;

public interface AuthService {
    
    UserEntity authenticate(String username, String password, String clientIp);
    
    UserEntity getCurrentUser();
    
    Long getCurrentUserId();
    
    String getCurrentUsername();
    
    List<String> getCurrentUserPermissions();
    
    List<MenuEntity> getCurrentUserMenus();
    
    boolean hasPermission(String permission);
    
    void logout(String clientIp);
}
