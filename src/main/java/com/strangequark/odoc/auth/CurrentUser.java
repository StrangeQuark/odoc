package com.strangequark.odoc.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Centralizes server-derived local identity lookup for application services. */
@Service
@Profile("local")
public class CurrentUser {
    public AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) return user;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A local session is required.");
    }

    public UUID requireId() {
        return require().id();
    }

    /** Use before credential-sensitive actions; normal page reads need only {@link #require()}. */
    public AuthenticatedUser requireFresh(Instant notBefore) {
        AuthenticatedUser user = require();
        if (user.authenticatedAt().isBefore(notBefore)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Re-enter your password to continue.");
        }
        return user;
    }
}
