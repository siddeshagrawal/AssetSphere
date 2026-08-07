package com.assetsphere.modules.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.assetsphere.modules.common.AuthenticationFailedException;
import com.assetsphere.modules.common.CurrentUser;
import com.assetsphere.modules.common.CurrentUserProvider;

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {
    @Override
    public CurrentUser requireCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return new CurrentUser(user.id(), user.email());
        }
        throw new AuthenticationFailedException("Authentication is required");
    }
}
