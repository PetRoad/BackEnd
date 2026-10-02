package com.petroad.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    static final String KEY = "test-only-key-do-not-deploy-0123456789abcdef";
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(JwtService.class).withPropertyValues("jwt.expiration-ms=60000");

    @Test void missingKeyPreventsStartup() {
        context.run(result -> assertThat(result).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "too-short", "change-this-secret-key-to-at-least-32-characters"})
    void unsafeKeyPreventsStartupWithoutPrintingTheKey(String key) {
        context.withPropertyValues("jwt.secret=" + key).run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseMessage(
                    "JWT_SECRET에 기존 기본값이 아닌 32바이트 이상의 무작위 키를 설정하세요.");
        });
    }

    @Test void validKeyStartsAndOnlyAcceptsItsOwnSignature() {
        context.withPropertyValues("jwt.secret=" + KEY).run(result -> {
            assertThat(result).hasNotFailed();
            JwtService service = result.getBean(JwtService.class);
            assertThat(service.parseUserId(service.issue(42L))).isEqualTo(42L);
            String forged = new JwtService("another-test-only-key-0123456789abcdef", 60000).issue(42L);
            assertThatThrownBy(() -> service.parseUserId(forged)).isInstanceOf(io.jsonwebtoken.JwtException.class);
        });
    }

    @Test void expiredTokenIsRejected() {
        String expired = Jwts.builder().subject("42").expiration(new Date(0))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> new JwtService(KEY, 60000).parseUserId(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
