package com.assetsphere.modules.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "assetsphere.auth.google")
public class GoogleOAuthProperties {
    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String frontendSuccessUrl;
    private String frontendFailureUrl;

    public void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || frontendSuccessUrl == null || frontendSuccessUrl.isBlank()
                || frontendFailureUrl == null || frontendFailureUrl.isBlank())
            throw new IllegalStateException("Google OAuth is enabled but not fully configured");
    }
}
