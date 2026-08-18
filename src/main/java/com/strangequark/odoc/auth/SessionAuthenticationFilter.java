package com.strangequark.odoc.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves the opaque HttpOnly session cookie without ever accepting a user ID from the client. */
@Component
@Profile("local")
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final LocalAuthService auth;

    SessionAuthenticationFilter(LocalAuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookie(request, AuthController.SESSION_COOKIE);
        AuthenticatedUser user = auth.authenticateSession(token);
        if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES));
        }
        chain.doFilter(request, response);
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
    }
}
