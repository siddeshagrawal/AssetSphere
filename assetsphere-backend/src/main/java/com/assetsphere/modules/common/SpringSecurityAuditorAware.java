package com.assetsphere.modules.common;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component("auditorAware")
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {
    @Override
    public Optional<UUID> getCurrentAuditor() {
        // TODO Phase 2: resolve the authenticated user ID from the JWT security context.
        return Optional.empty();
    }
}
