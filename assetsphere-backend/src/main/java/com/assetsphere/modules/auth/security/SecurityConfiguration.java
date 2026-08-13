package com.assetsphere.modules.auth.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.assetsphere.modules.common.web.ErrorResponse;
import com.assetsphere.modules.auth.application.GoogleOAuthProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${assetsphere.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Idempotent-Replay", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            ObjectMapper objectMapper,
            GoogleOAuthProperties googleProperties,
            ObjectProvider<GoogleOAuthSuccessHandler> googleSuccessHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        googleProperties.isEnabled() ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper, 401, "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper, 403, "ACCESS_DENIED", "Access is denied")))
                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults()).httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/oauth/exchange", "/api/v1/auth/providers", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workspaces/invitations/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/razorpay-local").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll().anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        if (googleProperties.isEnabled()) {
            GoogleOAuthSuccessHandler successHandler = googleSuccessHandler.getObject();
            http.oauth2Login(oauth -> oauth
                    .successHandler(successHandler)
                    .failureHandler((request, response, exception) -> response.sendRedirect(
                            org.springframework.web.util.UriComponentsBuilder
                                    .fromUriString(googleProperties.getFrontendFailureUrl())
                                    .queryParam("error", "oauth_failed")
                                    .build().encode().toUriString())));
        }
        return http.build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message, status, Instant.now(), MDC.get("correlationId"), List.of()));
    }
}
