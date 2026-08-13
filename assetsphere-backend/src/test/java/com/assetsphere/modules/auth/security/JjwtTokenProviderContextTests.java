package com.assetsphere.modules.auth.security;

import com.assetsphere.modules.auth.application.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JjwtTokenProviderContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(JwtProperties.class)
            .withBean(JjwtTokenProvider.class)
            .withPropertyValues(
                    "assetsphere.jwt.secret=01234567890123456789012345678901",
                    "assetsphere.jwt.access-token-expiration-seconds=900"
            );

    @Test
    void bindsJwtConfigurationToTheTypedProperties() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TokenService.class));
    }
}
