package com.petroad.backend.security;

import com.petroad.backend.api.AuthenticationFailedException;
import com.petroad.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ActiveUserInterceptor implements HandlerInterceptor {
    private final UserRepository users;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null || !users.existsById(userId)) {
            throw new AuthenticationFailedException("존재하지 않는 사용자입니다. 다시 로그인해주세요.");
        }
        return true;
    }
}