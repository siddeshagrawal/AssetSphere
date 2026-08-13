package com.assetsphere.modules.auth.security;

import java.io.IOException;
import java.util.List;

import com.assetsphere.modules.auth.application.TokenService;
import com.assetsphere.modules.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var authenticated = tokenProvider.authenticate(header.substring(7));
            CurrentUser user = new CurrentUser(authenticated.userId(), authenticated.email());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }
        filterChain.doFilter(request, response);
    }
}
