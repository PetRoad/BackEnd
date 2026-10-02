package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final DogRepository dogs;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginThrottle throttle;
    private final ClientIpResolver clientIp;
    private final String dummyPasswordHash;

    public AuthController(UserRepository users, DogRepository dogs, PasswordEncoder encoder, JwtService jwt,
                          LoginThrottle throttle, ClientIpResolver clientIp) {
        this.users = users;
        this.dogs = dogs;
        this.encoder = encoder;
        this.jwt = jwt;
        this.throttle = throttle;
        this.clientIp = clientIp;
        // Unknown accounts also perform BCrypt verification to reduce account enumeration by timing.
        this.dummyPasswordHash = encoder.encode("not-a-real-account-password");
    }

    public record Signup(@Email @NotBlank String email,
                         @NotBlank @Size(min = 8) @MaxPasswordBytes
                         @Schema(description = "최소 8글자, UTF-8 기준 최대 72바이트. 비밀번호를 자르지 않습니다.",
                                 minLength = 8, accessMode = Schema.AccessMode.WRITE_ONLY) String password,
                         @NotBlank String region, String dogName, String breed, DogSize dogSize,
                         java.time.LocalDate birthDate, String profileImage) {}

    public record Login(@Email @NotBlank String email,
                        @NotBlank @MaxPasswordBytes
                        @Schema(description = "UTF-8 기준 최대 72바이트. 초과 입력은 400으로 거부합니다.",
                                accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    public record Token(String accessToken, Long userId) {}

    @Operation(summary = "회원가입", description = "비밀번호는 최소 8글자이며 UTF-8 기준 72바이트까지 허용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일 중복",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    @PostMapping("/signup")
    public Token signup(@Valid @RequestBody Signup request) {
        if (users.findByEmail(request.email()).isPresent()) throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        User user = users.save(new User(request.email(), encoder.encode(request.password()), request.region()));
        if (request.dogName() != null) {
            dogs.save(new Dog(user, request.dogName(), request.breed(), request.dogSize(),
                    request.birthDate(), request.profileImage()));
        }
        return new Token(jwt.issue(user.getId()), user.getId());
    }

    @Operation(summary = "로그인", description = "계정별 실패 횟수와 IP별 요청 빈도를 제한합니다. "
            + "기본값: 15분 안에 실패 5회 시 15분 제한, IP당 1분에 요청 30회. "
            + "제한 기준은 서버 설정에 따라 달라질 수 있습니다.")
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

    @GetMapping("/health")
    public java.util.Map<String, String> health() { return java.util.Map.of("status", "ok"); }
}
