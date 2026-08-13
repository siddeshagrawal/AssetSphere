package com.assetsphere.modules.auth.application;

import com.assetsphere.modules.auth.api.GoogleOAuthConfigurationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GoogleOAuthConfigurationGuardAdapter implements GoogleOAuthConfigurationGuard {
    private final GoogleOAuthProperties properties;

    @Override
    public void validateIfEnabled() {
        if (properties.isEnabled()) properties.requireConfigured();
    }
}
