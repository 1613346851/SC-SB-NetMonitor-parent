package com.network.monitor.config;

import com.network.monitor.interceptor.AuthInterceptor;
import com.network.monitor.interceptor.CrossServiceSecurityInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CrossServiceSecurityInterceptor crossServiceSecurityInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(crossServiceSecurityInterceptor)
                .addPathPatterns("/api/inner/**")
                .order(0);

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/config/public/**",
                        "/api/inner/**"
                )
                .order(1);
    }
}
