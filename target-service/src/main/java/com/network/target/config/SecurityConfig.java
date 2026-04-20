package com.network.target.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");
        csrfTokenRepository.setParameterName("_csrf");

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                new AntPathRequestMatcher("/lib/**"),
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/webfonts/**"),
                                new AntPathRequestMatcher("/favicon.ico"),
                                new AntPathRequestMatcher("/target/lib/**"),
                                new AntPathRequestMatcher("/target/css/**"),
                                new AntPathRequestMatcher("/target/js/**"),
                                new AntPathRequestMatcher("/target/webfonts/**"),
                                new AntPathRequestMatcher("/target/test-files/**")
                        ).permitAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .requireCsrfProtectionMatcher(request -> {
                            String uri = request.getRequestURI();
                            return "POST".equalsIgnoreCase(request.getMethod())
                                    && uri != null
                                    && uri.endsWith("/target/csrf/safe-update-name");
                        })
                )
                .exceptionHandling(exception -> exception.accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"CSRF Token验证失败\",\"data\":{\"blocked_reason\":\"Spring Security CSRF Token 无效或缺失，请先重新获取 Token\"},\"timestamp\":" + System.currentTimeMillis() + "}");
                }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));


        return http.build();
    }
}
