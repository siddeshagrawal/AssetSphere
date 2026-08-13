package com.assetsphere.infrastructure.persistence;

import com.assetsphere.modules.common.security.CurrentUser;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component("auditorAware")
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {
    public static final UUID SYSTEM_AUDITOR_ID = new UUID(0L, 0L);

    @Override
    public Optional<UUID> getCurrentAuditor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(SYSTEM_AUDITOR_ID);
        }
        return authentication.getPrincipal() instanceof CurrentUser user
                ? Optional.of(user.id())
                : Optional.of(SYSTEM_AUDITOR_ID);
    }
}
