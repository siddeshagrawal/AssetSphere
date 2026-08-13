package com.assetsphere.modules.auth.security;

import com.assetsphere.modules.auth.application.GoogleOAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
class GoogleOAuthConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "assetsphere.auth.google", name = "enabled", havingValue = "true")
    ClientRegistrationRepository googleClientRegistrationRepository(GoogleOAuthProperties properties) {
        properties.requireConfigured();
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .registrationId("google").clientId(properties.getClientId()).clientSecret(properties.getClientSecret())
                .scope("openid", "profile", "email").clientName("Google").build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
