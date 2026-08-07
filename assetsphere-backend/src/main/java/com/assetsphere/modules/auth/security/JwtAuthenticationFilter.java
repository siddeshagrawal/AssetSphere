package com.assetsphere.modules.auth.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final com.assetsphere.modules.auth.application.TokenService tokenProvider;

    JwtAuthenticationFilter(com.assetsphere.modules.auth.application.TokenService tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var authenticated = tokenProvider.authenticate(header.substring(7));
            AuthenticatedUser user = new AuthenticatedUser(authenticated.userId(), authenticated.email());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }
        filterChain.doFilter(request, response);
    }
}
