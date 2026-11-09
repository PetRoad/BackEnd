package com.petroad.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    @Id @Column(length = 64) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false) private RefreshSession session;
    @Column(nullable = false) private boolean used;

    public RefreshToken(String tokenHash, RefreshSession session) {
        this.tokenHash = tokenHash;
        this.session = session;
    }

    public void consume() { used = true; }
}
