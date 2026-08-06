package com.assetsphere.infrastructure.security;

import java.util.UUID;

public interface JwtTokenProvider {
    IssuedAccessToken createAccessToken(UUID userId, String email);

    AuthenticatedUser parse(String token);

    record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
