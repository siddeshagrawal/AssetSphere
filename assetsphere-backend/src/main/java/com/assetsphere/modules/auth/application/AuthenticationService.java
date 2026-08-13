package com.assetsphere.modules.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties authProperties;
    private final ClockProvider clockProvider;
    private final WorkspaceFacade workspaceFacade;
    private final AuditService auditService;

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.findByNormalizedEmail(email).isPresent())
            throw new BusinessRuleViolationException("An account already exists for this email");
        try {
            User user = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()), request.displayName().trim()));
            var workspace = workspaceFacade.createPersonalWorkspace(user.getId(), user.getDisplayName());
            auditService.record(user.getId(), AuditAction.USER_REGISTERED, workspace.id(),
                    "USER", user.getId(), "{}");
            return new RegistrationResponse(UserResponse.from(user), workspace);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleViolationException("An account already exists for this email");
        }
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request, String clientMetadata) {
        String email = EmailNormalizer.normalize(request.email());
        Instant now = clockProvider.now();
        User user = userRepository.findByNormalizedEmail(email).orElse(null);
        if (user == null || !user.prepareForLogin(now) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (user != null) {
                user.recordFailedLogin(now, authProperties.getLoginMaxFailures(), authProperties.getLockDuration().toSeconds());
                auditService.record(user.getId(), AuditAction.USER_LOGIN_FAILED, null, "USER", user.getId(), "{}");
            }
            throw new AuthenticationFailedException("Invalid email or password");
        }
        user.recordSuccessfulLogin(now);
        auditService.record(user.getId(), AuditAction.USER_LOGIN_SUCCEEDED, null, "USER", user.getId(), "{}");
        return issueTokens(user, clientMetadata, now);
    }

    @Transactional
    public AuthenticationResponse refresh(RefreshRequest request, String clientMetadata) {
        Instant now = clockProvider.now();
        String hash = hash(request.refreshToken());
        RefreshToken previous = refreshTokenRepository.findByTokenHashForUpdate(hash).orElseThrow(() ->
                new AuthenticationFailedException("Invalid refresh token"));
        if (previous.getRevokedAt() != null)
            throw new AuthenticationFailedException("Refresh token reuse detected");
        if (previous.isExpired(now))
            throw new AuthenticationFailedException("Refresh token has expired");
        User user = userRepository.findById(previous.getUserId()).orElseThrow(() ->
                new AuthenticationFailedException("Invalid refresh token"));
        if (!user.prepareForLogin(now))
            throw new AuthenticationFailedException("Account is not active");
        AuthenticationResponse response = issueTokens(user, clientMetadata, now);
        RefreshToken replacement = refreshTokenRepository.findByTokenHash(hash(response.refreshToken())).orElseThrow();
        previous.revoke(now, replacement.getId());
        try {
            refreshTokenRepository.saveAndFlush(previous);
        } catch (OptimisticLockingFailureException exception) {
            throw new AuthenticationFailedException("Refresh token was already consumed");
        }
        return response;
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> token.revoke(clockProvider.now(), null));
        auditService.record(userId, AuditAction.USER_LOGGED_OUT, null, "USER",
                userId, "{}");
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new AuthenticationFailedException("Authenticated user no longer exists"));
        return new CurrentUserResponse(UserResponse.from(user), workspaceFacade.findWorkspacesForUser(userId));
    }

    @Transactional
    public AuthenticationResponse issueOAuthSession(UUID userId, String clientMetadata) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationFailedException("OAuth account is unavailable"));
        if (!user.prepareForLogin(clockProvider.now())) throw new AuthenticationFailedException("Account is not active");
        return issueTokens(user, clientMetadata, clockProvider.now());
    }

    private AuthenticationResponse issueTokens(User user, String clientMetadata, Instant now) {
        var access = tokenService.issueAccessToken(user.getId(), user.getNormalizedEmail());
        String rawRefresh = newRefreshToken();
        refreshTokenRepository.save(new RefreshToken(user.getId(), hash(rawRefresh), now,
                now.plus(authProperties.getRefreshTokenTtl()), clientMetadata));
        return new AuthenticationResponse("Bearer", access.value(), access.expiresInSeconds(),
                rawRefresh, authProperties.getRefreshTokenTtl().toSeconds());
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
