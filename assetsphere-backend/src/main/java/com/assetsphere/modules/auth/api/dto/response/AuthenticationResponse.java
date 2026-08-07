package com.assetsphere.modules.auth.api.dto.response;

public record AuthenticationResponse(
        String tokenType,
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        long refreshTokenExpiresInSeconds
) {
}
