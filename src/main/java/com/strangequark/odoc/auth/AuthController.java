package com.strangequark.odoc.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local account endpoints; registration is disabled outside the explicit local-development flag. */
@RestController
@Profile("local")
@RequestMapping("/api/v1/auth")
class AuthController {
    static final String SESSION_COOKIE = "ODOC_SESSION";
    static final String CSRF_COOKIE = "ODOC_CSRF";
    private final LocalAuthService auth;
    private final AccountRecoveryMailService recoveryMail;
    private final OdocAuthProperties properties;

    AuthController(LocalAuthService auth, AccountRecoveryMailService recoveryMail, OdocAuthProperties properties) {
        this.auth = auth;
        this.recoveryMail = recoveryMail;
        this.properties = properties;
    }

    @PostMapping("/register")
    ResponseEntity<SessionResponse> register(@Valid @RequestBody Credentials request, HttpServletResponse response) {
        UserAccount user = auth.register(request.email(), request.password());
        deliverEmailVerification(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(startSession(user, response));
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody Credentials request, HttpServletResponse response) {
        return startSession(auth.authenticate(request.email(), request.password()), response);
    }

    /** Consumes an email-delivered verifier supplied in a request body, never a URL path/query. */
    @PostMapping("/email-verification")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerificationRequest request) {
        auth.verifyEmail(request.verifier());
        return ResponseEntity.noContent().build();
    }

    /** Replaces any previous email code and requests delivery without exposing its verifier to the browser. */
    @PostMapping("/email-verification/resend")
    ResponseEntity<Void> resendEmailVerification() {
        AuthenticatedUser user = authenticatedUser();
        deliverEmailVerification(user.id());
        return ResponseEntity.noContent().build();
    }

    /** Always accepts a recovery request so this endpoint cannot reveal whether an account exists. */
    @PostMapping("/password-recovery/request")
    ResponseEntity<Void> requestPasswordRecovery(@Valid @RequestBody RecoveryRequest request) {
        LocalAuthService.PasswordRecoveryDelivery delivery = auth.requestPasswordRecovery(request.email());
        if (delivery != null) {
            try {
                recoveryMail.sendPasswordRecovery(delivery.email(), delivery.verifier());
            } catch (MailException ignored) {
                // The caller still receives the same response. Delivery monitoring/retries belong to
                // the production outbox integration; local Docker uses Mailpit for inspection.
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** Completes a password reset with a verifier supplied in the JSON body, never a URL. */
    @PostMapping("/password-recovery/complete")
    ResponseEntity<Void> completePasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        auth.completePasswordRecovery(request.verifier(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Changes a signed-in local account password and rotates its browser session. */
    @PostMapping("/password")
    SessionResponse changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletResponse response) {
        AuthenticatedUser currentUser = authenticatedUser();
        UserAccount user = auth.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
        clearCookie(response, SESSION_COOKIE, true);
        clearCookie(response, CSRF_COOKIE, false);
        return startSession(user, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.revoke(cookie(request, SESSION_COOKIE));
        clearCookie(response, SESSION_COOKIE, true);
        clearCookie(response, CSRF_COOKIE, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    SessionResponse session() {
        AuthenticatedUser user = authenticatedUser();
        return new SessionResponse(user.id(), user.email(), null, auth.isEmailVerified(user.id()));
    }

    private AuthenticatedUser authenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AuthenticatedUser user)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "A local session is required.");
        }
        return user;
    }

    private SessionResponse startSession(UserAccount user, HttpServletResponse response) {
        LocalAuthService.SessionTokens tokens = auth.createSession(user);
        setCookie(response, SESSION_COOKIE, tokens.sessionToken(), true, properties.sessionTtl());
        setCookie(response, CSRF_COOKIE, tokens.csrfToken(), false, properties.sessionTtl());
        return new SessionResponse(user.id(), auth.emailFor(user.id()), tokens.expiresAt(), auth.isEmailVerified(user.id()));
    }

    private void deliverEmailVerification(UserAccount user) {
        deliverEmailVerification(user.id());
    }

    private void deliverEmailVerification(UUID userId) {
        String verifier = auth.issueEmailVerification(userId);
        if (verifier == null) return;
        try {
            recoveryMail.sendEmailVerification(auth.emailFor(userId), verifier);
        } catch (MailException ignored) {
            // A local request remains successful even if Mailpit is temporarily unavailable. Production
            // delivery/retry monitoring belongs to the future durable outbox integration.
        }
    }

    private void setCookie(HttpServletResponse response, String name, String value, boolean httpOnly, Duration ttl) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(properties.secureCookies());
        cookie.setPath("/");
        cookie.setMaxAge(Math.toIntExact(ttl.toSeconds()));
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name, boolean httpOnly) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(properties.secureCookies());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
    }

    record Credentials(@Email @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}
    record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    record RecoveryRequest(@Email @NotBlank @Size(max = 320) String email) {}
    record PasswordRecoveryRequest(
            @NotBlank @Size(max = 256) String verifier,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    record VerificationRequest(@NotBlank @Size(max = 256) String verifier) {}
    record SessionResponse(
            UUID userId,
            String email,
            java.time.Instant expiresAt,
            boolean emailVerified) {}
}
