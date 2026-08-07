package com.assetsphere.modules.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserTests {
    @Test
    void locksAccountAfterConfiguredFailedAttempts() {
        User user = new User("user@example.test", "hash", "User");
        Instant now = Instant.parse("2026-08-07T00:00:00Z");

        user.recordFailedLogin(now, 2, 300);
        user.recordFailedLogin(now, 2, 300);

        assertThat(user.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(user.getLockedUntil()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void unlocksExpiredLockBeforeAuthentication() {
        User user = new User("user@example.test", "hash", "User");
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        user.recordFailedLogin(now, 1, 60);

        assertThat(user.prepareForLogin(now.plusSeconds(61))).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getFailedLoginCount()).isZero();
    }
}
