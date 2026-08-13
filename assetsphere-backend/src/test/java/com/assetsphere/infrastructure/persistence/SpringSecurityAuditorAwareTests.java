package com.assetsphere.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.common.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SpringSecurityAuditorAwareTests {

    private final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesCurrentUserIdFromAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        CurrentUser user = new CurrentUser(userId, "auditor@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );

        assertThat(auditorAware.getCurrentAuditor()).contains(userId);
    }

    @Test
    void resolvesSystemAuditorWhenNoAuthenticatedUserExists() {
        assertThat(auditorAware.getCurrentAuditor()).contains(SpringSecurityAuditorAware.SYSTEM_AUDITOR_ID);
    }
}
