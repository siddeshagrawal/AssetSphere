package com.assetsphere.modules.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import com.assetsphere.modules.common.security.CurrentUser;
import com.assetsphere.modules.common.security.CurrentUserProvider;

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {
    @Override
    public CurrentUser requireCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user) {
            return user;
        }
        throw new AuthenticationFailedException("Authentication is required");
    }
}
