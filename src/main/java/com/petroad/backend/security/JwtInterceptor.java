package com.petroad.backend.security;

import com.petroad.backend.api.AuthenticationFailedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) throw new AuthenticationFailedException("인증 토큰이 필요합니다.");
        try {
            req.setAttribute("userId", jwtService.parseUserId(auth.substring(7)));
            return true;
        } catch (Exception e) {
            throw new AuthenticationFailedException("유효하지 않은 인증 토큰입니다.");
        }
    }
}
