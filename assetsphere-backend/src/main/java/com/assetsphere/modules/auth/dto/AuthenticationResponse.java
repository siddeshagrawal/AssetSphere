package com.assetsphere.modules.auth.dto;

public record AuthenticationResponse(String tokenType, String accessToken, long accessTokenExpiresInSeconds,
                                     String refreshToken, long refreshTokenExpiresInSeconds) {
}
