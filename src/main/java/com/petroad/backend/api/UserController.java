package com.petroad.backend.api;

import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository users;

    public record MyInfo(Long userId, String email, String region) {}
    public record RegionRequest(@NotBlank String region) {}

    @Operation(summary = "내 정보 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public MyInfo me(HttpServletRequest request) {
        User u = findCurrentUser(request);
        return new MyInfo(u.getId(), u.getEmail(), u.getRegion());
    }

    @Operation(summary = "내 동네(지역) 설정")
    @Transactional
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/region")
    public MyInfo updateRegion(HttpServletRequest request, @Valid @RequestBody RegionRequest r) {
        User u = findCurrentUser(request);
        u.updateRegion(r.region());
        return new MyInfo(u.getId(), u.getEmail(), u.getRegion());
    }

    private User findCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }
}