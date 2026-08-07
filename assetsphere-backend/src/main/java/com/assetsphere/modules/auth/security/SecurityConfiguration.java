package com.assetsphere.modules.auth.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
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

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper, 401, "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper, 403, "ACCESS_DENIED", "Access is denied")))
                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults()).httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll().anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message, status, Instant.now(), MDC.get("correlationId"), List.of()));
    }
}
