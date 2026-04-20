package com.network.monitor.util;

import com.network.monitor.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    private static AuthService authService;

    @Autowired
    public void setAuthService(AuthService authService) {
        SecurityUtil.authService = authService;
    }

    public static String getCurrentUsername() {
        if (authService == null) {
            return "system";
        }
        String username = authService.getCurrentUsername();
        return username != null ? username : "system";
    }

    public static boolean isAuthenticated() {
        if (authService == null) {
            return false;
        }
        return authService.getCurrentUserId() != null;
    }
}
