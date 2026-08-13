package com.assetsphere.modules.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "oauth_identities") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthIdentity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 32) private String provider;
    @Column(name = "provider_subject", nullable = false) private String providerSubject;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public OAuthIdentity(UUID userId, String provider, String subject, Instant now) {
        this.id = UUID.randomUUID(); this.userId = userId; this.provider = provider; this.providerSubject = subject; this.createdAt = now;
    }
}
