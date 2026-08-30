package com.memospace.config;

import com.memospace.security.AdminAuthorizationInterceptor;
import com.memospace.security.AdminSessionBoundaryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {
    private final AdminAuthorizationInterceptor adminInterceptor;
    private final AdminSessionBoundaryInterceptor sessionBoundary;

    public AdminWebConfig(AdminAuthorizationInterceptor adminInterceptor,
                          AdminSessionBoundaryInterceptor sessionBoundary) {
        this.adminInterceptor = adminInterceptor;
        this.sessionBoundary = sessionBoundary;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
        registry.addInterceptor(sessionBoundary)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**", "/api/auth/**");
    }
}
