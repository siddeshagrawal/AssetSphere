package com.assetsphere.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import com.assetsphere.infrastructure.config.ApplicationProperties;
import com.assetsphere.modules.common.AuthenticationFailedException;

@Component
class JjwtTokenProvider implements JwtTokenProvider {
    private final ApplicationProperties properties;
    private SecretKey signingKey;

    JjwtTokenProvider(ApplicationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateSecret() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ASSETSPHERE_JWT_SECRET must be at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IssuedAccessToken createAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        long lifetime = properties.getJwt().getAccessTokenExpirationSeconds();
        String token = Jwts.builder().subject(userId.toString()).claim("email", email).claim("token_type", "access")
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(lifetime)))
                .signWith(signingKey).compact();
        return new IssuedAccessToken(token, lifetime);
    }

    @Override
    public AuthenticatedUser parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            if (!"access".equals(claims.get("token_type", String.class))) {
                throw new AuthenticationFailedException("Invalid access token");
            }
            return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class));
        } catch (AuthenticationFailedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationFailedException("Invalid or expired access token");
        }
    }
}
