package com.strangequark.odoc.auth;

import com.strangequark.odoc.audit.AuditPublisher;
import com.strangequark.odoc.encryption.DataEncryptionKey;
import com.strangequark.odoc.encryption.DataEncryptionKeyProvider;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Database-backed throttles, shared by every API replica and keyed only by HMAC-derived values. */
@Service
@Profile("local")
class AuthRateLimitService {
    private final AuthRateLimitBucketRepository buckets;
    private final DataEncryptionKeyProvider keys;
    private final OdocAuthProperties properties;
    private final AuditPublisher audit;

    AuthRateLimitService(
            AuthRateLimitBucketRepository buckets, DataEncryptionKeyProvider keys, OdocAuthProperties properties, AuditPublisher audit) {
        this.buckets = buckets;
        this.keys = keys;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void assertLoginPermitted(String normalizedEmail, String origin) {
        Instant now = Instant.now();
        String account = accountKey(normalizedEmail);
        String source = originKey(origin);
        if (blocked(account, now) || blocked(source, now)) {
            recordRateLimit("security.login.rate_limited", account + ":" + source, now);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Please try again later.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordLoginFailure(String normalizedEmail, String origin) {
        Instant now = Instant.now();
        recordFailure(accountKey(normalizedEmail), properties.loginAccountAttemptLimit(), now);
        recordFailure(originKey(origin), properties.loginIpAttemptLimit(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordLoginSuccess(String normalizedEmail, String origin) {
        Instant now = Instant.now();
        bucket(accountKey(normalizedEmail), now).recordSuccess(now);
        bucket(originKey(origin), now).recordSuccess(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void assertInvitationExchangePermitted(String origin) {
        Instant now = Instant.now();
        String source = invitationExchangeKey(origin);
        if (blocked(source, now)) {
            recordRateLimit("security.invitation_exchange.rate_limited", source, now);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many invitation attempts. Please try again later.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordInvitationExchangeFailure(String origin) {
        recordFailure(invitationExchangeKey(origin), properties.invitationExchangeAttemptLimit(), Instant.now(),
                properties.invitationExchangeRateLimitWindow(), properties.invitationExchangeRateLimitBlock());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordInvitationExchangeSuccess(String origin) {
        bucket(invitationExchangeKey(origin), Instant.now()).recordSuccess(Instant.now());
    }

    private boolean blocked(String key, Instant now) {
        return buckets.findWithLockByBucketKey(key).map(bucket -> bucket.blockedAt(now)).orElse(false);
    }

    private void recordFailure(String key, int threshold, Instant now) {
        bucket(key, now).recordFailure(now, threshold, properties.loginRateLimitWindow(), properties.loginRateLimitBlock());
    }

    private void recordFailure(String key, int threshold, Instant now, java.time.Duration window, java.time.Duration block) {
        bucket(key, now).recordFailure(now, threshold, window, block);
    }

    private AuthRateLimitBucket bucket(String key, Instant now) {
        return buckets.findWithLockByBucketKey(key).orElseGet(() -> {
            try {
                return buckets.saveAndFlush(new AuthRateLimitBucket(key, now));
            } catch (DataIntegrityViolationException duplicate) {
                return buckets.findWithLockByBucketKey(key).orElseThrow(() -> duplicate);
            }
        });
    }

    private String accountKey(String normalizedEmail) {
        return "login-account:" + digest(normalizedEmail == null ? "invalid" : normalizedEmail);
    }

    private String originKey(String origin) {
        return "login-origin:" + digest(origin == null || origin.isBlank() ? "unknown" : origin);
    }

    private String invitationExchangeKey(String origin) {
        return "invitation-exchange-origin:" + digest(origin == null || origin.isBlank() ? "unknown" : origin);
    }

    private String digest(String value) {
        try {
            DataEncryptionKey key = keys.activeKey(
                    new SecurityScope(SecurityScopeKind.INSTANCE, properties.instanceScopeId()), EncryptionPurpose.AUTH_RATE_LIMIT);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.key().getEncoded(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive an authentication rate-limit key.", exception);
        }
    }

    private void recordRateLimit(String action, String opaqueKey, Instant now) {
        // The HMAC-derived bucket identifier is used only as an idempotency component. It is not
        // audit payload metadata, so emails/IP addresses/credentials never enter the outbox.
        audit.record(null, null, action, "instance_security_policy", properties.instanceScopeId(), "blocked",
                "security-rate-limit:" + action + ":" + opaqueKey + ":" + now.getEpochSecond() / 60);
    }
}
