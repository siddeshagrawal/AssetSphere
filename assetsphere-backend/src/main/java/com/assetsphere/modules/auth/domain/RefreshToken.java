package com.assetsphere.modules.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import com.assetsphere.modules.common.persistence.BaseEntity;

@Getter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;
    @Column(name = "client_metadata", length = 256)
    private String clientMetadata;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt, String clientMetadata) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.clientMetadata = clientMetadata;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now, UUID replacementId) {
        if (revokedAt == null) {
            revokedAt = now;
            replacedByTokenId = replacementId;
        }
    }
}
