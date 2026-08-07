package com.assetsphere.modules.common.security;

import java.util.UUID;

/**
 * Authenticated identity available to business modules without exposing security framework types.
 */
public record CurrentUser(
        UUID id,
        String email
) {
}
