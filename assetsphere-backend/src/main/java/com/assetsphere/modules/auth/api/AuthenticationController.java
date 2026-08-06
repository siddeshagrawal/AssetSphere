package com.assetsphere.modules.auth.api;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assetsphere.modules.auth.AuthenticationService;
import com.assetsphere.modules.auth.dto.LoginRequest;
import com.assetsphere.modules.auth.dto.LogoutRequest;
import com.assetsphere.modules.auth.dto.RefreshRequest;
import com.assetsphere.modules.auth.dto.RegisterRequest;
import com.assetsphere.modules.common.ApiResponse;
import com.assetsphere.modules.common.ClockProvider;
import com.assetsphere.modules.common.CurrentUserProvider;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthenticationController {
    private final AuthenticationService service;
    private final ClockProvider clock;
    private final CurrentUserProvider currentUser;

    AuthenticationController(AuthenticationService service, ClockProvider clock, CurrentUserProvider currentUser) {
        this.service = service;
        this.clock = clock;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.register(request), clock));
    }

    @PostMapping("/login")
    ApiResponse<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.login(request, http.getHeader("User-Agent")), clock);
    }

    @PostMapping("/refresh")
    ApiResponse<?> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.refresh(request, http.getHeader("User-Agent")), clock);
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        UUID id = currentUser.requireCurrentUser().id();
        service.logout(id, request.refreshToken());
        return ApiResponse.success(null, clock);
    }

    @GetMapping("/me")
    ApiResponse<?> me() {
        return ApiResponse.success(service.currentUser(currentUser.requireCurrentUser().id()), clock);
    }
}
