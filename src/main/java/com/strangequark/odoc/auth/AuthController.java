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
import com.strangequark.odoc.workspace.WorkspaceService;
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

/** Local account endpoints; production activation remains an explicit deployment decision. */
@RestController
@Profile("local")
@RequestMapping("/api/v1/auth")
class AuthController {
    static final String SESSION_COOKIE = "ODOC_SESSION";
    static final String CSRF_COOKIE = "ODOC_CSRF";
    private final LocalAuthService auth;
    private final AccountRecoveryMailService recoveryMail;
    private final OdocAuthProperties properties;
    private final AuthSecurityEventService securityEvents;
    private final WorkspaceService workspaces;

    AuthController(
            LocalAuthService auth,
            AccountRecoveryMailService recoveryMail,
            OdocAuthProperties properties,
            AuthSecurityEventService securityEvents,
            WorkspaceService workspaces) {
        this.auth = auth;
        this.recoveryMail = recoveryMail;
        this.properties = properties;
        this.securityEvents = securityEvents;
        this.workspaces = workspaces;
    }

    @PostMapping("/register")
    ResponseEntity<SessionResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        if (!properties.localRegistrationEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Local account registration is disabled.");
        }
        UserAccount user = properties.inviteOnly()
                ? auth.requireActiveAccount(workspaces.registerInvitedAccount(
                        request.email(), request.password(), request.invitationVerifier()))
                : auth.registerSelfService(request.email(), request.password());
        deliverEmailVerification(user);
        securityEvents.record("ACCOUNT_REGISTERED", "SUCCESS", user.id(), origin(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(startSession(user, response));
    }

    /** Public capability discovery; no identity, workspace, or provider secret is returned. */
    @GetMapping("/registration-policy")
    RegistrationPolicyResponse registrationPolicy() {
        return new RegistrationPolicyResponse(properties.localRegistrationEnabled(), properties.inviteOnly());
    }

    @PostMapping("/login")
    SessionResponse login(
            @Valid @RequestBody Credentials request, HttpServletRequest servletRequest, HttpServletResponse response) {
        try {
            UserAccount user = auth.authenticate(request.email(), request.password(), origin(servletRequest));
            securityEvents.record("LOGIN", "SUCCESS", user.id(), origin(servletRequest));
            return startSession(user, response);
        } catch (org.springframework.web.server.ResponseStatusException failure) {
            securityEvents.record("LOGIN", "FAILURE", null, origin(servletRequest));
            throw failure;
        }
    }

    /** Consumes an email-delivered verifier supplied in a request body, never a URL path/query. */
    @PostMapping("/email-verification")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerificationRequest request, HttpServletRequest servletRequest) {
        UserAccount user = auth.verifyEmail(request.verifier());
        securityEvents.record("EMAIL_VERIFIED", "SUCCESS", user.id(), origin(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** Replaces any previous email code and requests delivery without exposing its verifier to the browser. */
    @PostMapping("/email-verification/resend")
    ResponseEntity<Void> resendEmailVerification(HttpServletRequest servletRequest) {
        AuthenticatedUser user = authenticatedUser();
        deliverEmailVerification(user.id());
        securityEvents.record("EMAIL_VERIFICATION_RESENT", "SUCCESS", user.id(), origin(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** Always accepts a recovery request so this endpoint cannot reveal whether an account exists. */
    @PostMapping("/password-recovery/request")
    ResponseEntity<Void> requestPasswordRecovery(@Valid @RequestBody RecoveryRequest request, HttpServletRequest servletRequest) {
        LocalAuthService.PasswordRecoveryDelivery delivery = auth.requestPasswordRecovery(request.email());
        if (delivery != null) {
            try {
                recoveryMail.sendPasswordRecovery(delivery.email(), delivery.verifier());
            } catch (MailException ignored) {
                // The caller still receives the same response. Delivery monitoring/retries belong to
                // the production outbox integration; local Docker uses Mailpit for inspection.
            }
        }
        securityEvents.record("PASSWORD_RECOVERY_REQUESTED", "ACCEPTED", null, origin(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** Completes a password reset with a verifier supplied in the JSON body, never a URL. */
    @PostMapping("/password-recovery/complete")
    ResponseEntity<Void> completePasswordRecovery(
            @Valid @RequestBody PasswordRecoveryRequest request, HttpServletRequest servletRequest) {
        UserAccount user = auth.completePasswordRecovery(request.verifier(), request.newPassword());
        securityEvents.record("PASSWORD_RECOVERY_COMPLETED", "SUCCESS", user.id(), origin(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** Changes a signed-in local account password and rotates its browser session. */
    @PostMapping("/password")
    SessionResponse changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        AuthenticatedUser currentUser = authenticatedUser();
        UserAccount user = auth.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
        clearCookie(response, SESSION_COOKIE, true);
        clearCookie(response, CSRF_COOKIE, false);
        securityEvents.record("PASSWORD_CHANGED", "SUCCESS", user.id(), origin(servletRequest));
        return startSession(user, response);
    }

    /** Refreshes the timestamp used by future credential-sensitive operations without rotating page state. */
    @PostMapping("/fresh-authentication")
    ResponseEntity<Void> refreshAuthentication(
            @Valid @RequestBody FreshAuthenticationRequest request, HttpServletRequest servletRequest) {
        AuthenticatedUser user = authenticatedUser();
        auth.refreshAuthentication(cookie(servletRequest, SESSION_COOKIE), user.id(), request.password());
        securityEvents.record("FRESH_AUTHENTICATION", "SUCCESS", user.id(), origin(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser user = authenticatedUser();
        auth.revoke(cookie(request, SESSION_COOKIE));
        clearCookie(response, SESSION_COOKIE, true);
        clearCookie(response, CSRF_COOKIE, false);
        securityEvents.record("LOGOUT", "SUCCESS", user.id(), origin(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    SessionResponse session() {
        AuthenticatedUser user = authenticatedUser();
        return new SessionResponse(user.id(), user.email(), null, user.emailVerified());
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
        return new SessionResponse(user.id(), auth.emailFor(user.id()), tokens.expiresAt(), user.emailVerified());
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

    private static String origin(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    record Credentials(@Email @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}
    record RegistrationRequest(
            @Email @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 256) String invitationVerifier) {}
    record RegistrationPolicyResponse(boolean registrationEnabled, boolean inviteOnly) {}
    record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    record RecoveryRequest(@Email @NotBlank @Size(max = 320) String email) {}
    record PasswordRecoveryRequest(
            @NotBlank @Size(max = 256) String verifier,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    record VerificationRequest(@NotBlank @Size(max = 256) String verifier) {}
    record FreshAuthenticationRequest(@NotBlank @Size(max = 128) String password) {}
    record SessionResponse(
            UUID userId,
            String email,
            java.time.Instant expiresAt,
            boolean emailVerified) {}
}
