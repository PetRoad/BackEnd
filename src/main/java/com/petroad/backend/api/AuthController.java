package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.security.*;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginThrottle throttle;
    private final ClientIpResolver clientIp;
    private final String dummyPasswordHash;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                          LoginThrottle throttle, ClientIpResolver clientIp) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.throttle = throttle;
        this.clientIp = clientIp;
        this.dummyPasswordHash = encoder.encode("not-a-real-account-password");
    }

    public record Signup(
            @Email @NotBlank String email,
            @NotBlank
            @Pattern(regexp = "^[\\x21-\\x7E]{8,14}$", message = "비밀번호는 영문, 숫자, 특수문자만 사용하여 공백 없이 8자 이상 14자 이하로 입력해주세요.")
            @Schema(description = "공백 없는 영문·숫자·특수문자 8~14자", minLength = 8, maxLength = 14,
                    accessMode = Schema.AccessMode.WRITE_ONLY) String password
    ) {
    }

    public record SignupResponse(Long userId, String email) {
    }

    public record Login(@Email @NotBlank String email, @NotBlank @MaxPasswordBytes
                        @Schema(description = "UTF-8 기준 최대 72바이트. 초과 입력은 400으로 거부합니다.",
                                accessMode = Schema.AccessMode.WRITE_ONLY) String password) {
    }

    public record Token(String accessToken, Long userId) {
    }

    @Operation(summary = "회원가입", description = "이메일과 비밀번호로 가입합니다. 동네·반려견은 가입 후 설정하고, 토큰은 로그인 API에서 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일 중복",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody Signup r) {
        if (users.findByEmail(r.email()).isPresent())
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        User u = users.save(new User(r.email(), encoder.encode(r.password())));
        return new SignupResponse(u.getId(), u.getEmail());
    }

    @Operation(summary = "로그인", description = "기본값: 계정별 최근 15분에 실패 5회 이후 15분 제한, IP당 1분에 요청 30회. 기준은 서버 설정으로 조절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 (비밀번호 72바이트 초과 포함)",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class))),
            @ApiResponse(responseCode = "429", description = "로그인 제한. Retry-After 초 이후 다시 시도하세요.",
                    headers = @Header(name = "Retry-After", description = "재시도까지 대기할 초",
                            schema = @Schema(type = "integer", minimum = "1")),
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    @PostMapping("/login")
    public Token login(@Valid @RequestBody Login request, HttpServletRequest servletRequest) {
        try (LoginThrottle.Attempt attempt = throttle.begin(request.email(), clientIp.resolve(servletRequest))) {
            User user = users.findByEmail(request.email()).orElse(null);
            boolean matches = encoder.matches(request.password(), user == null ? dummyPasswordHash : user.getPassword());
            if (user == null || !matches) {
                attempt.failed();
                throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
            }
            Token token = new Token(jwt.issue(user.getId()), user.getId());
            attempt.succeeded();
            return token;
        }
    }

    @Operation(summary = "서버상태 확인")
    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok");
    }
}
