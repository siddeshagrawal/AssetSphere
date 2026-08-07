package com.assetsphere.modules.auth;

import java.util.UUID;

/** Port owned by Authentication; cryptographic implementations live in infrastructure. */
public interface TokenService {
    IssuedAccessToken issueAccessToken(UUID userId, String email);

    AuthenticatedPrincipal authenticate(String token);

    record IssuedAccessToken(String value, long expiresInSeconds) { }

    record AuthenticatedPrincipal(UUID userId, String email) { }
}
