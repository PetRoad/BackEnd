package com.petroad.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false) private User user;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked;

    public RefreshSession(User user, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public void revoke() { revoked = true; }
}
