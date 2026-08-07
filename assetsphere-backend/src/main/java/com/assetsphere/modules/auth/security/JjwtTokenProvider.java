package com.assetsphere.modules.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import com.assetsphere.modules.auth.TokenService;
import com.assetsphere.modules.common.AuthenticationFailedException;

@Component
class JjwtTokenProvider implements TokenService {
    private final String secret;
    private final long accessTokenExpirationSeconds;
    private SecretKey signingKey;

    JjwtTokenProvider(@org.springframework.beans.factory.annotation.Value("${assetsphere.jwt.secret}") String secret,
                      @org.springframework.beans.factory.annotation.Value("${assetsphere.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds) {
        this.secret = secret;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ASSETSPHERE_JWT_SECRET must be at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IssuedAccessToken issueAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        long lifetime = accessTokenExpirationSeconds;
        String token = Jwts.builder().subject(userId.toString()).claim("email", email).claim("token_type", "access")
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(lifetime)))
                .signWith(signingKey).compact();
        return new IssuedAccessToken(token, lifetime);
    }

    @Override
    public AuthenticatedPrincipal authenticate(String token) {
        try {
            var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            if (!"access".equals(claims.get("token_type", String.class))) {
                throw new AuthenticationFailedException("Invalid access token");
            }
            return new AuthenticatedPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class));
        } catch (AuthenticationFailedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationFailedException("Invalid or expired access token");
        }
    }
}
