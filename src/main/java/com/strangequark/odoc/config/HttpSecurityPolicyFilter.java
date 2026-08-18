package com.strangequark.odoc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP policy that applies before Spring Security. It makes the safe defaults
 * executable: APIs are never browser-cacheable, ambient cross-origin requests
 * are rejected, and a direct API listener never accidentally trusts headers a
 * caller can forge. A deployment that needs CORS must name every origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Profile("!thin-slice")
class HttpSecurityPolicyFilter extends OncePerRequestFilter {
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Forwarded-Port");
    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Content-Type, X-Odoc-Csrf, If-Match, X-Request-Id";
    private final OdocSecurityProperties properties;

    HttpSecurityPolicyFilter(OdocSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        applyResponsePolicy(request, response);
        if (properties.rejectForwardedHeaders() && hasForwardedHeader(request)) {
            writeProblem(request, response, HttpStatus.BAD_REQUEST,
                    "Forwarded headers are accepted only from a configured trusted proxy.");
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            if (!isValidOrigin(origin) || (!sameOrigin(request, origin) && !properties.allows(origin))) {
                writeProblem(request, response, HttpStatus.FORBIDDEN,
                        "This origin is not permitted to call the Odoc API.");
                return;
            }
            applyCors(response, origin);
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean hasForwardedHeader(HttpServletRequest request) {
        return FORWARDED_HEADERS.stream().anyMatch(header -> {
            String value = request.getHeader(header);
            return value != null && !value.isBlank();
        });
    }

    private static boolean isValidOrigin(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean sameOrigin(HttpServletRequest request, String origin) {
        String scheme = request.isSecure() ? "https" : request.getScheme();
        int port = request.getServerPort();
        String expected = scheme + "://" + request.getServerName()
                + (isDefaultPort(scheme, port) ? "" : ":" + port);
        return expected.equalsIgnoreCase(origin);
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static void applyResponsePolicy(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/actuator/")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("Pragma", "no-cache");
        }
    }

    private static void applyCors(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }

    private static void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        String requestId = String.valueOf(request.getAttribute(RequestIdFilter.HEADER));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"https://odoc.local/problems/" + status.value()
                + "\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\",\"instance\":\""
                + json(request.getRequestURI()) + "\",\"requestId\":\"" + json(requestId) + "\"}");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
