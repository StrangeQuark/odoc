package com.strangequark.odoc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a correlation identifier that callers can include with support reports. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestedId = request.getHeader(HEADER);
        String requestId = requestedId != null && requestedId.matches("[A-Za-z0-9._-]{8,128}")
                ? requestedId
                : UUID.randomUUID().toString();
        request.setAttribute(HEADER, requestId);
        response.setHeader(HEADER, requestId);
        filterChain.doFilter(request, response);
    }
}
