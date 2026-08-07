package com.assetsphere.modules.auth.persistence;
import com.assetsphere.modules.auth.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndExpiresAtBefore(UUID userId, Instant expiresAt);
}
