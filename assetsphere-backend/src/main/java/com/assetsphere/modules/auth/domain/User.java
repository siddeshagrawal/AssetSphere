package com.assetsphere.modules.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import com.assetsphere.modules.common.persistence.BaseEntity;

@Getter
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
    private String normalizedEmail;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String normalizedEmail, String passwordHash, String displayName) {
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = AccountStatus.ACTIVE;
        this.emailVerified = false;
    }

    public static User oauth(String normalizedEmail, String passwordHash, String displayName) {
        User user = new User(normalizedEmail, passwordHash, displayName);
        user.emailVerified = true;
        return user;
    }

    public boolean prepareForLogin(Instant now) {
        if (status == AccountStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now)) {
            status = AccountStatus.ACTIVE;
            lockedUntil = null;
            failedLoginCount = 0;
        }
        return status == AccountStatus.ACTIVE;
    }

    public void recordFailedLogin(Instant now, int maximumFailures, long lockSeconds) {
        failedLoginCount++;
        if (failedLoginCount >= maximumFailures) {
            status = AccountStatus.LOCKED;
            lockedUntil = now.plusSeconds(lockSeconds);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        status = AccountStatus.ACTIVE;
        lastLoginAt = now;
    }
}
