package com.assetsphere.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.auth.persistence.RefreshTokenRepository;
import com.assetsphere.modules.auth.persistence.UserRepository;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuthPropertiesConfiguration.class)
            .withBean(AuthenticationService.class)
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(RefreshTokenRepository.class, () -> mock(RefreshTokenRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(TokenService.class, () -> mock(TokenService.class))
            .withBean(ClockProvider.class, () -> mock(ClockProvider.class))
            .withBean(WorkspaceFacade.class, () -> mock(WorkspaceFacade.class))
            .withBean(AuditService.class, () -> mock(AuditService.class));

    @Test
    void bindsAuthPropertiesAndConstructsAuthenticationService() {
        contextRunner.withPropertyValues(
                "assetsphere.auth.refresh-token-ttl=PT2H",
                "assetsphere.auth.login-max-failures=7",
                "assetsphere.auth.lock-duration=PT30M"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuthenticationService.class);

            AuthProperties properties = context.getBean(AuthProperties.class);
            assertThat(properties.getRefreshTokenTtl()).isEqualTo(Duration.ofHours(2));
            assertThat(properties.getLoginMaxFailures()).isEqualTo(7);
            assertThat(properties.getLockDuration()).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class AuthPropertiesConfiguration {
    }
}
