package com.strangequark.odoc.workspace;

import com.strangequark.odoc.auth.OdocAuthProperties;
import com.strangequark.odoc.auth.InvitationRateLimitService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keeps invitation verifiers in a POST body and upgrades them to a short-lived HttpOnly capability. */
@RestController
@RequestMapping("/api/v1/invitations")
class WorkspaceInvitationController {
    static final String CAPABILITY_COOKIE = "odoc_invite_capability";
    private final WorkspaceService workspaces;
    private final OdocAuthProperties properties;
    private final ObjectProvider<InvitationRateLimitService> rateLimits;

    WorkspaceInvitationController(
            WorkspaceService workspaces, OdocAuthProperties properties, ObjectProvider<InvitationRateLimitService> rateLimits) {
        this.workspaces = workspaces;
        this.properties = properties;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/{routeId}/exchange")
    ResponseEntity<Void> exchange(
            @PathVariable UUID routeId,
            @Valid @RequestBody ExchangeWorkspaceInvitationRequest request,
            HttpServletRequest requestContext,
            HttpServletResponse response) {
        String origin = requestContext.getRemoteAddr();
        InvitationRateLimitService rateLimit = rateLimits.getIfAvailable();
        if (rateLimit != null) rateLimit.assertPermitted(origin);
        WorkspaceService.InvitationCapability capability;
        try {
            capability = workspaces.exchangeInvitation(routeId, request.verifier());
            if (rateLimit != null) rateLimit.recordSuccess(origin);
        } catch (org.springframework.web.server.ResponseStatusException failure) {
            if (rateLimit != null) rateLimit.recordFailure(origin);
            throw failure;
        }
        Cookie cookie = new Cookie(CAPABILITY_COOKIE, capability.token());
        cookie.setHttpOnly(true);
        cookie.setSecure(properties.secureCookies());
        cookie.setPath("/api/v1/invitations");
        cookie.setMaxAge(Math.toIntExact(Duration.between(Instant.now(), capability.expiresAt()).toSeconds()));
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept")
    ResponseEntity<Void> accept(HttpServletRequest request) {
        workspaces.acceptInvitationCapability(cookie(request, CAPABILITY_COOKIE));
        return ResponseEntity.noContent().build();
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
    }
}
