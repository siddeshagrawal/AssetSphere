package com.assetsphere.modules.auth.dto;
import java.time.Instant; import java.util.UUID;
import com.assetsphere.modules.auth.domain.User;
public record UserResponse(UUID id, String email, String displayName, String status, boolean emailVerified, Instant lastLoginAt) { public static UserResponse from(User user) { return new UserResponse(user.getId(), user.getNormalizedEmail(), user.getDisplayName(), user.getStatus().name(), user.isEmailVerified(), user.getLastLoginAt()); } }
