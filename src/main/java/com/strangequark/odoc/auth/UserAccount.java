package com.strangequark.odoc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
class UserAccount {
    @Id private UUID id;
    @Column(name = "email_lookup_token", nullable = false, unique = true) private byte[] emailLookupToken;
    @Column(name = "email_envelope", nullable = false) private String emailEnvelope;
    @Column(name = "password_hash", nullable = false, length = 512) private String passwordHash;
    @Column(nullable = false) private boolean active;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserAccount() {}

    UserAccount(UUID id, byte[] emailLookupToken, String emailEnvelope, String passwordHash, Instant createdAt) {
        this.id = id;
        this.emailLookupToken = Arrays.copyOf(emailLookupToken, emailLookupToken.length);
        this.emailEnvelope = emailEnvelope;
        this.passwordHash = passwordHash;
        this.active = true;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    String emailEnvelope() { return emailEnvelope; }
    String passwordHash() { return passwordHash; }
    boolean active() { return active; }
    boolean emailVerified() { return emailVerifiedAt != null; }
    void markEmailVerified(Instant now) { emailVerifiedAt = now; }
    void changePasswordHash(String nextPasswordHash) { passwordHash = nextPasswordHash; }
}
