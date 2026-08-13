package com.assetsphere.modules.auth.api;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assetsphere.modules.auth.application.AuthenticationService;
import com.assetsphere.modules.auth.application.GoogleOAuthProperties;
import com.assetsphere.modules.auth.application.OAuthLoginService;
import com.assetsphere.modules.auth.api.dto.request.OAuthExchangeRequest;
import com.assetsphere.modules.auth.api.dto.request.LoginRequest;
import com.assetsphere.modules.auth.api.dto.request.LogoutRequest;
import com.assetsphere.modules.auth.api.dto.request.RefreshRequest;
import com.assetsphere.modules.auth.api.dto.request.RegisterRequest;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.security.CurrentUserProvider;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final OAuthLoginService oauthLoginService;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final ClockProvider clock;
    private final CurrentUserProvider currentUser;

    @PostMapping("/register")
    ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authenticationService.register(request), clock));
    }

    @PostMapping("/login")
    ApiResponse<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.success(authenticationService.login(request, http.getHeader("User-Agent")), clock);
    }

    @PostMapping("/refresh")
    ApiResponse<?> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return ApiResponse.success(authenticationService.refresh(request, http.getHeader("User-Agent")), clock);
    }

    @PostMapping("/oauth/exchange")
    ApiResponse<?> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request, HttpServletRequest http) {
        return ApiResponse.success(oauthLoginService.exchange(request.code(), http.getHeader("User-Agent")), clock);
    }

    @GetMapping("/providers")
    ApiResponse<?> providers() {
        return ApiResponse.success(java.util.Map.of("google", googleOAuthProperties.isEnabled()), clock);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        UUID id = currentUser.requireCurrentUser().id();
        authenticationService.logout(id, request.refreshToken());
        return ApiResponse.success(null, clock);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<?> me() {
        return ApiResponse.success(authenticationService.currentUser(currentUser.requireCurrentUser().id()), clock);
    }
}
