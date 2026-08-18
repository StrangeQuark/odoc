package com.strangequark.odoc.auth;

import com.strangequark.odoc.encryption.DataEncryptionKey;
import com.strangequark.odoc.encryption.DataEncryptionKeyProvider;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes durable, privacy-preserving security events independently of a failed request transaction. */
@Service
@Profile("local")
class AuthSecurityEventService {
    private final AuthSecurityEventRepository events;
    private final DataEncryptionKeyProvider keys;
    private final OdocAuthProperties properties;

    AuthSecurityEventService(AuthSecurityEventRepository events, DataEncryptionKeyProvider keys, OdocAuthProperties properties) {
        this.events = events;
        this.keys = keys;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(String type, String outcome, UUID userId, String origin) {
        events.save(new AuthSecurityEvent(type, outcome, userId, originToken(origin), Instant.now()));
    }

    private byte[] originToken(String origin) {
        try {
            DataEncryptionKey key = keys.activeKey(
                    new SecurityScope(SecurityScopeKind.INSTANCE, properties.instanceScopeId()), EncryptionPurpose.AUTH_AUDIT);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.key().getEncoded(), "HmacSHA256"));
            return mac.doFinal((origin == null || origin.isBlank() ? "unknown" : origin).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive an authentication audit token.", exception);
        }
    }
}
