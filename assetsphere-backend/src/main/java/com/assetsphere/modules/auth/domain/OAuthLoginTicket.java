package com.assetsphere.modules.auth.domain;

import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "oauth_login_tickets") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthLoginTicket {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "ticket_hash", nullable = false, unique = true, length = 64) private String ticketHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public OAuthLoginTicket(UUID userId, String hash, Instant now) {
        id = UUID.randomUUID(); this.userId = userId; ticketHash = hash; createdAt = now; expiresAt = now.plusSeconds(120);
    }

    public void consume(Instant now) {
        if (consumedAt != null || !now.isBefore(expiresAt)) throw new AuthenticationFailedException("OAuth login code is invalid or expired");
        consumedAt = now;
    }
}
