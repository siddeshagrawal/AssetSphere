package com.assetsphere.modules.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenTests {
    @Test
    void revokedTokenCannotBeUsedAgain() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "a".repeat(64), now, now.plusSeconds(600), null);

        token.revoke(now, UUID.randomUUID());

        assertThat(token.isUsable(now.plusSeconds(1))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(now);
    }
}
