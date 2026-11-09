package com.petroad.backend.repository;

import com.petroad.backend.domain.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Query("select t.session.id from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> findSessionId(@Param("hash") String hash);
}
