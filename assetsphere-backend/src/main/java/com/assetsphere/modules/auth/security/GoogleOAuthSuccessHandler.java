package com.assetsphere.modules.auth.security;

import com.assetsphere.modules.auth.application.GoogleOAuthProperties;
import com.assetsphere.modules.auth.application.OAuthLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix = "assetsphere.auth.google", name = "enabled", havingValue = "true")
class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthLoginService logins;
    private final GoogleOAuthProperties properties;

    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                   Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth) || !"google".equals(oauth.getAuthorizedClientRegistrationId()))
            throw new ServletException("Unsupported OAuth provider");
        String code = logins.completeGoogleLogin(oauth.getPrincipal().getAttributes());
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        response.sendRedirect(UriComponentsBuilder.fromUriString(properties.getFrontendSuccessUrl())
                .queryParam("code", code).build().encode().toUriString());
    }
}
