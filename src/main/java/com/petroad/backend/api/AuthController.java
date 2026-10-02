package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public record Signup(
            @Email @NotBlank String email,
            @NotBlank
            @Pattern(regexp = "^[\\x21-\\x7E]{8,14}$", message = "비밀번호는 영문, 숫자, 특수문자만 사용하여 공백 없이 8자 이상 14자 이하로 입력해주세요.")
            String password
    ) {
    }

    public record SignupResponse(Long userId, String email) {
    }

    public record Login(@Email String email, @NotBlank String password) {
    }

    public record Token(String accessToken, Long userId) {
    }

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody Signup r) {
        if (users.findByEmail(r.email()).isPresent())
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        User u = users.save(new User(r.email(), encoder.encode(r.password())));
        return new SignupResponse(u.getId(), u.getEmail());
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public Token login(@Valid @RequestBody Login r) {
        User u = users.findByEmail(r.email()).orElseThrow(() -> new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!encoder.matches(r.password(), u.getPassword()))
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        return new Token(jwt.issue(u.getId()), u.getId());
    }

    @Operation(summary = "서버상태 확인")
    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok");
    }
}
