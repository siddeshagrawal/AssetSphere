package com.assetsphere.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.auth.api.dto.request.RefreshRequest;
import com.assetsphere.modules.auth.domain.RefreshToken;
import com.assetsphere.modules.auth.domain.User;
import com.assetsphere.modules.auth.persistence.RefreshTokenRepository;
import com.assetsphere.modules.auth.persistence.UserRepository;
import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceRefreshTests {
    @Test
    void optimisticRotationRaceBecomesSafeAuthenticationFailure() {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        UUID userId = UUID.randomUUID();
        RefreshToken previous = new RefreshToken(userId, "hash", now.minusSeconds(1), now.plusSeconds(3600), "test");
        RefreshToken replacement = new RefreshToken(userId, "replacement", now, now.plusSeconds(3600), "test");
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        TokenService tokens = mock(TokenService.class);
        User user = mock(User.class);
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(previous));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(replacement));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(user.prepareForLogin(now)).thenReturn(true);
        when(user.getId()).thenReturn(userId);
        when(user.getNormalizedEmail()).thenReturn("user@example.com");
        when(tokens.issueAccessToken(userId, "user@example.com"))
                .thenReturn(new TokenService.IssuedAccessToken("access", 900));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.saveAndFlush(previous)).thenThrow(
                new ObjectOptimisticLockingFailureException(RefreshToken.class, UUID.randomUUID()));
        AuthenticationService service = new AuthenticationService(users, refreshTokens, mock(PasswordEncoder.class),
                tokens, new AuthProperties(), () -> now, mock(WorkspaceFacade.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("refresh-token"), "test"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Refresh token was already consumed");
    }
}
