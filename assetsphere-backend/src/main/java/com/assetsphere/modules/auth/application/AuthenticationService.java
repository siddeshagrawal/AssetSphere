package com.assetsphere.modules.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.common.text.EmailNormalizer;
import com.assetsphere.modules.auth.domain.RefreshToken;
import com.assetsphere.modules.auth.persistence.RefreshTokenRepository;
import com.assetsphere.modules.auth.domain.User;
import com.assetsphere.modules.auth.persistence.UserRepository;
import com.assetsphere.modules.auth.api.dto.response.AuthenticationResponse;
import com.assetsphere.modules.auth.api.dto.response.CurrentUserResponse;
import com.assetsphere.modules.auth.api.dto.request.LoginRequest;
import com.assetsphere.modules.auth.api.dto.request.RefreshRequest;
import com.assetsphere.modules.auth.api.dto.request.RegisterRequest;
import com.assetsphere.modules.auth.api.dto.response.RegistrationResponse;
import com.assetsphere.modules.auth.api.dto.response.UserResponse;
import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final TokenService jwt;
    private final long refreshTokenExpirationSeconds;
    private final int loginMaxFailures;
    private final long lockDurationSeconds;
    private final ClockProvider clock;
    private final WorkspaceFacade workspaces;
    private final AuditService audit;

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (users.findByNormalizedEmail(email).isPresent())
            throw new BusinessRuleViolationException("An account already exists for this email");
        try {
            User user = users.saveAndFlush(new User(email, passwords.encode(request.password()), request.displayName().trim()));
            var workspace = workspaces.createPersonalWorkspace(user.getId(), user.getDisplayName());
            audit.record(user.getId(), AuditAction.USER_REGISTERED, workspace.id(), "USER", user.getId(), "{}");
            return new RegistrationResponse(UserResponse.from(user), workspace);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleViolationException("An account already exists for this email");
        }
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request, String clientMetadata) {
        String email = EmailNormalizer.normalize(request.email());
        Instant now = clock.now();
        User user = users.findByNormalizedEmail(email).orElse(null);
        if (user == null || !user.prepareForLogin(now) || !passwords.matches(request.password(), user.getPasswordHash())) {
            if (user != null) {
                user.recordFailedLogin(now, loginMaxFailures, lockDurationSeconds);
                audit.record(user.getId(), AuditAction.USER_LOGIN_FAILED, null, "USER", user.getId(), "{}");
            }
            throw new AuthenticationFailedException("Invalid email or password");
        }
        user.recordSuccessfulLogin(now);
        audit.record(user.getId(), AuditAction.USER_LOGIN_SUCCEEDED, null, "USER", user.getId(), "{}");
        return issueTokens(user, clientMetadata, now);
    }

    @Transactional
    public AuthenticationResponse refresh(RefreshRequest request, String clientMetadata) {
        Instant now = clock.now();
        String hash = hash(request.refreshToken());
        RefreshToken previous = refreshTokens.findByTokenHash(hash).orElseThrow(() ->
                new AuthenticationFailedException("Invalid refresh token"));
        if (previous.getRevokedAt() != null)
            throw new AuthenticationFailedException("Refresh token reuse detected");
        if (previous.isExpired(now))
            throw new AuthenticationFailedException("Refresh token has expired");
        User user = users.findById(previous.getUserId()).orElseThrow(() ->
                new AuthenticationFailedException("Invalid refresh token"));
        if (!user.prepareForLogin(now))
            throw new AuthenticationFailedException("Account is not active");
        AuthenticationResponse response = issueTokens(user, clientMetadata, now);
        RefreshToken replacement = refreshTokens.findByTokenHash(hash(response.refreshToken())).orElseThrow();
        previous.revoke(now, replacement.getId());
        return response;
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> token.revoke(clock.now(), null));
        audit.record(userId, AuditAction.USER_LOGGED_OUT, null, "USER",
                userId, "{}");
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID userId) {
        User user = users.findById(userId).orElseThrow(() ->
                new AuthenticationFailedException("Authenticated user no longer exists"));
        return new CurrentUserResponse(UserResponse.from(user), workspaces.findWorkspacesForUser(userId));
    }

    private AuthenticationResponse issueTokens(User user, String clientMetadata, Instant now) {
        var access = jwt.issueAccessToken(user.getId(), user.getNormalizedEmail());
        String rawRefresh = newRefreshToken();
        refreshTokens.save(new RefreshToken(user.getId(), hash(rawRefresh), now,
                now.plusSeconds(refreshTokenExpirationSeconds), clientMetadata));
        return new AuthenticationResponse("Bearer", access.value(), access.expiresInSeconds(),
                rawRefresh, refreshTokenExpirationSeconds);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
