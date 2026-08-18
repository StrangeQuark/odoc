package com.strangequark.odoc.auth;

import com.strangequark.odoc.audit.AuditPublisher;
import com.strangequark.odoc.encryption.DataEncryptionKey;
import com.strangequark.odoc.encryption.DataEncryptionKeyProvider;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import com.strangequark.odoc.workspace.WorkspaceProvisioningService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Local email/password account flow backed by encrypted identity data and opaque sessions. */
@Service
@Profile("local")
public class LocalAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RECOVERY_TTL = Duration.ofHours(1);
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final AuthActionTokenRepository actionTokens;
    private final ManagedRecordEncryption encryption;
    private final DataEncryptionKeyProvider keys;
    private final PasswordEncoder passwords;
    private final OdocAuthProperties properties;
    private final WorkspaceProvisioningService workspaces;
    private final AuthRateLimitService rateLimits;
    private final AuditPublisher audit;

    LocalAuthService(
            UserAccountRepository users,
            AuthSessionRepository sessions,
            AuthActionTokenRepository actionTokens,
            ManagedRecordEncryption encryption,
            DataEncryptionKeyProvider keys,
            PasswordEncoder passwords,
            OdocAuthProperties properties,
            WorkspaceProvisioningService workspaces,
            AuthRateLimitService rateLimits,
            AuditPublisher audit) {
        this.users = users;
        this.sessions = sessions;
        this.actionTokens = actionTokens;
        this.encryption = encryption;
        this.keys = keys;
        this.passwords = passwords;
        this.properties = properties;
        this.workspaces = workspaces;
        this.rateLimits = rateLimits;
        this.audit = audit;
    }

    @Transactional
    UserAccount registerSelfService(String email, String password) {
        if (!properties.selfServiceRegistrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found.");
        }
        return createAccount(email, password, true);
    }

    /** Creates an account only after the workspace invitation service has validated its verifier. */
    @Transactional
    public UUID registerFromInvitation(String email, String password) {
        return createAccount(email, password, false).id();
    }

    private UserAccount createAccount(String email, String password, boolean provisionOwnedWorkspace) {
        String normalized = normalizeEmail(email);
        byte[] lookup = lookupToken(normalized);
        if (users.findByEmailLookupToken(lookup).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for that email.");
        }
        validatePassword(password);
        UUID id = UUID.randomUUID();
        String envelope = EncryptedRecordCodec.encode(encryption.encrypt(
                new EncryptionContext(instanceScope(), id, EncryptionPurpose.IDENTITY, 1),
                normalized.getBytes(StandardCharsets.UTF_8)));
        UserAccount user = users.save(new UserAccount(id, lookup, envelope, passwords.encode(password), Instant.now()));
        if (provisionOwnedWorkspace) {
            workspaces.ensureOwnedWorkspace(user.id());
        }
        return user;
    }

    UserAccount requireActiveAccount(UUID userId) {
        return users.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(LocalAuthService::invalidCredentials);
    }

    @Transactional(readOnly = true)
    UserAccount authenticate(String email, String password, String origin) {
        String normalized;
        try {
            normalized = normalizeEmail(email);
        } catch (ResponseStatusException invalid) {
            rateLimits.assertLoginPermitted(null, origin);
            rateLimits.recordLoginFailure(null, origin);
            throw invalidCredentials();
        }
        rateLimits.assertLoginPermitted(normalized, origin);
        UserAccount user = users.findByEmailLookupToken(lookupToken(normalized))
                .filter(UserAccount::active)
                .orElse(null);
        if (user == null || !passwords.matches(password, user.passwordHash())) {
            rateLimits.recordLoginFailure(normalized, origin);
            throw invalidCredentials();
        }
        rateLimits.recordLoginSuccess(normalized, origin);
        return user;
    }

    @Transactional
    SessionTokens createSession(UserAccount user) {
        String sessionToken = randomToken();
        String csrfToken = randomToken();
        Instant now = Instant.now();
        AuthSession session = new AuthSession(
                user.id(), sha256(sessionToken), sha256(csrfToken), now.plus(properties.sessionTtl()), now);
        sessions.save(session);
        audit.record(null, user.id(), "auth.session.created", "user_account", user.id(), "success",
                "auth-session-create-" + session.id());
        return new SessionTokens(sessionToken, csrfToken, session.expiresAt());
    }

    /**
     * Generates a short-lived verifier for a local development delivery adapter. The database
     * receives only its SHA-256 hash; production email delivery will consume the same port.
     */
    @Transactional
    String issueEmailVerification(UUID userId) {
        UserAccount user = users.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        if (user.emailVerified()) return null;
        String verifier = randomToken();
        Instant now = Instant.now();
        actionTokens.findAllByUserIdAndActionTypeAndConsumedAtIsNull(user.id(), AuthActionType.EMAIL_VERIFICATION)
                .forEach(token -> token.consume(now));
        actionTokens.save(new AuthActionToken(
                user.id(), AuthActionType.EMAIL_VERIFICATION, sha256(verifier), now.plus(EMAIL_VERIFICATION_TTL), now));
        return verifier;
    }

    @Transactional
    UserAccount verifyEmail(String verifier) {
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link is invalid or expired.");
        }
        Instant now = Instant.now();
        AuthActionToken token = actionTokens.findByTokenHash(sha256(verifier))
                .filter(candidate -> candidate.isUsable(AuthActionType.EMAIL_VERIFICATION, now))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "This verification link is invalid or expired."));
        UserAccount user = users.findById(token.userId())
                .filter(UserAccount::active)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "This verification link is invalid or expired."));
        token.consume(now);
        user.markEmailVerified(now);
        return user;
    }

    @Transactional(readOnly = true)
    boolean isEmailVerified(UUID userId) {
        return users.findById(userId).map(UserAccount::emailVerified).orElse(false);
    }

    /**
     * Creates a password-recovery delivery only for an active local account. Callers must always
     * return the same response whether this returns a value or not, to avoid account enumeration.
     */
    @Transactional
    PasswordRecoveryDelivery requestPasswordRecovery(String email) {
        String normalized;
        try {
            normalized = normalizeEmail(email);
        } catch (ResponseStatusException ignored) {
            return null;
        }
        UserAccount user = users.findByEmailLookupToken(lookupToken(normalized))
                .filter(UserAccount::active)
                .orElse(null);
        if (user == null) return null;

        Instant now = Instant.now();
        String verifier = randomToken();
        actionTokens.findAllByUserIdAndActionTypeAndConsumedAtIsNull(user.id(), AuthActionType.PASSWORD_RECOVERY)
                .forEach(token -> token.consume(now));
        actionTokens.save(new AuthActionToken(
                user.id(), AuthActionType.PASSWORD_RECOVERY, sha256(verifier), now.plus(PASSWORD_RECOVERY_TTL), now));
        return new PasswordRecoveryDelivery(emailFor(user.id()), verifier);
    }

    /** Consumes a one-time recovery verifier before replacing the password and invalidating sessions. */
    @Transactional
    UserAccount completePasswordRecovery(String verifier, String nextPassword) {
        validatePassword(nextPassword);
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) {
            throw invalidRecoveryCode();
        }
        Instant now = Instant.now();
        AuthActionToken token = actionTokens.findByTokenHash(sha256(verifier))
                .filter(candidate -> candidate.isUsable(AuthActionType.PASSWORD_RECOVERY, now))
                .orElseThrow(LocalAuthService::invalidRecoveryCode);
        UserAccount user = users.findById(token.userId())
                .filter(UserAccount::active)
                .orElseThrow(LocalAuthService::invalidRecoveryCode);
        token.consume(now);
        user.changePasswordHash(passwords.encode(nextPassword));
        sessions.findAllByUserIdAndRevokedAtIsNull(user.id()).forEach(session -> session.revoke(now));
        return user;
    }

    /**
     * Replaces a local password after proving possession of the current password. Every prior
     * browser session is revoked so a stolen session cannot survive a credential change.
     */
    @Transactional
    UserAccount changePassword(UUID userId, String currentPassword, String nextPassword) {
        UserAccount user = users.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(LocalAuthService::invalidCredentials);
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        validatePassword(nextPassword);
        user.changePasswordHash(passwords.encode(nextPassword));
        Instant now = Instant.now();
        sessions.findAllByUserIdAndRevokedAtIsNull(user.id()).forEach(session -> session.revoke(now));
        return user;
    }

    @Transactional(readOnly = true)
    AuthenticatedUser authenticateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return null;
        return sessions.findByTokenHash(sha256(sessionToken))
                .filter(session -> session.usableAt(Instant.now()))
                .flatMap(session -> users.findById(session.userId())
                        .filter(UserAccount::active)
                        .map(user -> asAuthenticated(user, session)))
                .orElse(null);
    }

    /**
     * Verifies the double-submit CSRF value against the currently usable opaque session.
     * The raw value is never persisted or returned once the response which created it ends.
     */
    @Transactional(readOnly = true)
    boolean hasValidCsrfToken(String sessionToken, String csrfToken) {
        if (sessionToken == null || sessionToken.isBlank() || csrfToken == null || csrfToken.isBlank()) return false;
        return sessions.findByTokenHash(sha256(sessionToken))
                .filter(session -> session.usableAt(Instant.now()))
                .map(session -> session.hasCsrfHash(sha256(csrfToken)))
                .orElse(false);
    }

    @Transactional
    void revoke(String sessionToken) {
        sessions.findByTokenHash(sha256(sessionToken)).ifPresent(session -> session.revoke(Instant.now()));
    }

    @Transactional
    void refreshAuthentication(String sessionToken, UUID userId, String password) {
        AuthSession session = sessions.findByTokenHash(sha256(sessionToken))
                .filter(candidate -> candidate.usableAt(Instant.now()) && candidate.userId().equals(userId))
                .orElseThrow(LocalAuthService::invalidCredentials);
        UserAccount user = users.findById(userId).filter(UserAccount::active).orElseThrow(LocalAuthService::invalidCredentials);
        if (!passwords.matches(password, user.passwordHash())) throw invalidCredentials();
        session.markFreshlyAuthenticated(Instant.now());
    }

    @Transactional(readOnly = true)
    public String emailFor(UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new String(encryption.decrypt(
                new EncryptionContext(instanceScope(), user.id(), EncryptionPurpose.IDENTITY, 1),
                EncryptedRecordCodec.decode(user.emailEnvelope())), StandardCharsets.UTF_8);
    }

    private AuthenticatedUser asAuthenticated(UserAccount user, AuthSession session) {
        return new AuthenticatedUser(user.id(), emailFor(user.id()), user.emailVerified(), session.authenticatedAt());
    }

    /** Looks up an active local account without exposing its encrypted identity storage to feature code. */
    @Transactional(readOnly = true)
    public LocalAccountSummary findActiveAccount(String email) {
        UserAccount user = users.findByEmailLookupToken(lookupToken(normalizeEmail(email)))
                .filter(UserAccount::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        return new LocalAccountSummary(user.id(), emailFor(user.id()));
    }

    /** Encrypts an invitation recipient independently from a user-account row. */
    public EncryptedEmailAddress encryptInvitationEmail(UUID invitationId, String email) {
        String normalized = normalizeEmail(email);
        String envelope = EncryptedRecordCodec.encode(encryption.encrypt(
                new EncryptionContext(instanceScope(), invitationId, EncryptionPurpose.IDENTITY, 1),
                normalized.getBytes(StandardCharsets.UTF_8)));
        return new EncryptedEmailAddress(lookupToken(normalized), envelope);
    }

    public String decryptInvitationEmail(UUID invitationId, String envelope) {
        return new String(encryption.decrypt(
                new EncryptionContext(instanceScope(), invitationId, EncryptionPurpose.IDENTITY, 1),
                EncryptedRecordCodec.decode(envelope)), StandardCharsets.UTF_8);
    }

    public boolean invitationIsFor(UUID invitationId, String envelope, String email) {
        return java.security.MessageDigest.isEqual(
                decryptInvitationEmail(invitationId, envelope).getBytes(StandardCharsets.UTF_8),
                normalizeEmail(email).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] lookupToken(String normalizedEmail) {
        try {
            DataEncryptionKey key = keys.activeKey(instanceScope(), EncryptionPurpose.IDENTITY_LOOKUP);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.key().getEncoded(), "HmacSHA256"));
            return mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive an identity lookup token.", exception);
        }
    }

    private SecurityScope instanceScope() {
        return new SecurityScope(SecurityScopeKind.INSTANCE, properties.instanceScopeId());
    }

    static String normalizeEmail(String email) {
        if (email == null) throw invalidCredentials();
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw invalidCredentials();
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 12-128 characters.");
        }
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    private static ResponseStatusException invalidRecoveryCode() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This password reset code is invalid or expired.");
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    record SessionTokens(String sessionToken, String csrfToken, Instant expiresAt) {}
    record PasswordRecoveryDelivery(String email, String verifier) {}
    public record EncryptedEmailAddress(byte[] lookupToken, String envelope) {
        public EncryptedEmailAddress { lookupToken = Arrays.copyOf(lookupToken, lookupToken.length); }
        @Override public byte[] lookupToken() { return Arrays.copyOf(lookupToken, lookupToken.length); }
    }
}
