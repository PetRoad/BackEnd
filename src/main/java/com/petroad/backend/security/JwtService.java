package com.petroad.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMs;
    public JwtService(@Value("${jwt.secret:}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.equals("change-this-secret-key-to-at-least-32-characters")) {
            throw new IllegalArgumentException("JWT_SECRET에 기존 기본값이 아닌 32바이트 이상의 무작위 키를 설정하세요.");
        }
        if (expirationMs <= 0) throw new IllegalArgumentException("JWT 만료 시간은 양수여야 합니다.");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMs = expirationMs;
    }
    public String issue(Long userId) { return Jwts.builder().subject(userId.toString()).issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis()+expirationMs)).signWith(key).compact(); }
    public Long parseUserId(String token) { return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}
