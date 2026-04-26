package com.network.monitor.config;

import com.network.monitor.interceptor.AuthInterceptor;
import com.network.monitor.interceptor.CrossServiceSecurityInterceptor;
import com.network.monitor.interceptor.PageAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CrossServiceSecurityInterceptor crossServiceSecurityInterceptor;
    
    @Autowired
    private PageAuthInterceptor pageAuthInterceptor;
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(crossServiceSecurityInterceptor)
                .addPathPatterns("/api/inner/**")
                .order(0);

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/config/public/**",
                        "/api/config/alert.sound",
                        "/api/inner/**"
                )
                .order(1);
        
        registry.addInterceptor(pageAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/api/**",
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/lib/**",
                        "/images/**",
                        "/fonts/**",
                        "/webfonts/**",
                        "/favicon.ico",
                        "/error"
                )
                .order(2);
    }
}
