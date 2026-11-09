package com.petroad.backend.config;

import com.petroad.backend.security.ActiveUserInterceptor;
import com.petroad.backend.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration @RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor jwtInterceptor;
    private final ActiveUserInterceptor activeUserInterceptor;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                               jakarta.servlet.http.HttpServletResponse response, Object handler) {
                response.setHeader("Cache-Control", "no-store");
                return true;
            }
        }).addPathPatterns("/api/**");
        registry.addInterceptor(jwtInterceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health", "/api/hello");
        registry.addInterceptor(activeUserInterceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health", "/api/hello");
    }
}