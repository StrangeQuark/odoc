package com.strangequark.odoc.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces a per-session double-submit token for cookie-authenticated mutations.
 *
 * <p>Legacy local Basic authentication remains stateless during the incremental migration, so a
 * request with no session cookie is intentionally allowed through for the security chain to
 * authenticate (or reject) normally.
 */
@Component
@Profile("local")
public class SessionCsrfFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Odoc-Csrf";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final LocalAuthService auth;

    SessionCsrfFilter(LocalAuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String sessionToken = cookie(request, AuthController.SESSION_COOKIE);
        if (sessionToken.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (!auth.hasValidCsrfToken(sessionToken, request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"https://odoc.local/problems/403\","
                    + "\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"A valid CSRF token is required for this request.\","
                    + "\"instance\":\"" + json(request.getRequestURI()) + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
