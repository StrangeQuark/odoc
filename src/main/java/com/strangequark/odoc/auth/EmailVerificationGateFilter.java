package com.strangequark.odoc.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits an unverified session to the account-verification boundary; workspace data is never loaded first. */
@Component
@Profile("local")
public class EmailVerificationGateFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") || path.startsWith("/actuator/") || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication instanceof org.springframework.security.core.Authentication current
                ? current.getPrincipal()
                : null;
        if (principal instanceof AuthenticatedUser user && !user.emailVerified()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"https://odoc.local/problems/email-verification-required\","
                    + "\"title\":\"Email verification required\",\"status\":403,"
                    + "\"detail\":\"Verify your email address before accessing Odoc.\","
                    + "\"instance\":\"" + json(request.getRequestURI()) + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
