package com.petroad.backend.security;

import com.petroad.backend.api.AuthenticationFailedException;
import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class RefreshTokenService {
    private final RefreshSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final JwtService jwt;
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshSessionRepository sessions, RefreshTokenRepository tokens,
                               JwtService jwt, Clock clock,
                               @Value("${jwt.refresh-expiration:30d}") Duration lifetime) {
        if (lifetime.isZero() || lifetime.isNegative())
            throw new IllegalArgumentException("리프레시 토큰 만료 시간은 양수여야 합니다.");
        this.sessions = sessions;
        this.tokens = tokens;
        this.jwt = jwt;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public record TokenPair(@Schema(description = "보호된 API의 Bearer 인증에 사용하는 JWT") String accessToken,
                            Long userId,
                            @Schema(description = "재발급·로그아웃 전용 토큰. 재발급할 때마다 새 값으로 교체") String refreshToken,
                            @Schema(description = "액세스 토큰 유효 시간(초)", example = "900") long expiresIn,
                            @Schema(description = "리프레시 토큰의 남은 유효 시간(초)", example = "2592000") long refreshExpiresIn) {}

    @Transactional
    public TokenPair issue(User user) {
        RefreshSession session = sessions.save(new RefreshSession(user, clock.instant().plus(lifetime)));
        return issuePair(session);
    }

    // Replay revocation must commit even though the HTTP request returns 401.
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenPair refresh(String rawToken) {
        String hash = hash(rawToken);
        RefreshSession session = lockedSession(hash);
        if (session.isRevoked() || !clock.instant().isBefore(session.getExpiresAt())) throw invalid();
        RefreshToken token = tokens.findById(hash).orElseThrow(RefreshTokenService::invalid);
        if (token.isUsed()) {
            session.revoke();
            throw invalid();
        }
        token.consume();
        return issuePair(session);
    }

    @Transactional
    public void logout(String rawToken) {
        lockedSession(hash(rawToken)).revoke();
    }

    private RefreshSession lockedSession(String hash) {
        UUID id = tokens.findSessionId(hash).orElseThrow(RefreshTokenService::invalid);
        return sessions.lockById(id).orElseThrow(RefreshTokenService::invalid);
    }

    private TokenPair issuePair(RefreshSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(hash(raw), session));
        Long userId = session.getUser().getId();
        return new TokenPair(jwt.issue(userId), userId, raw, jwt.expirationSeconds(),
                Math.max(0, Duration.between(clock.instant(), session.getExpiresAt()).toSeconds()));
    }

    private static String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static AuthenticationFailedException invalid() {
        return new AuthenticationFailedException("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.");
    }
}
