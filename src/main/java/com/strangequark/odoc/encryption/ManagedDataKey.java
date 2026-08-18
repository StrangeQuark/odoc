package com.strangequark.odoc.encryption;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Persisted encrypted key material; it never stores a plaintext DEK. */
@Entity
@Table(name = "managed_data_keys")
class ManagedDataKey {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_kind", nullable = false, length = 32)
    private SecurityScopeKind scopeKind;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EncryptionPurpose purpose;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "wrapping_key_version", nullable = false)
    private int wrappingKeyVersion;

    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ManagedDataKey() {
    }

    ManagedDataKey(
            SecurityScope scope,
            EncryptionPurpose purpose,
            int keyVersion,
            int wrappingKeyVersion,
            byte[] wrappedDek,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.scopeKind = scope.kind();
        this.scopeId = scope.id();
        this.purpose = purpose;
        this.keyVersion = keyVersion;
        this.wrappingKeyVersion = wrappingKeyVersion;
        this.wrappedDek = Arrays.copyOf(wrappedDek, wrappedDek.length);
        this.status = "ACTIVE";
        this.createdAt = createdAt;
    }

    SecurityScope scope() { return new SecurityScope(scopeKind, scopeId); }
    EncryptionPurpose purpose() { return purpose; }
    int keyVersion() { return keyVersion; }
    int wrappingKeyVersion() { return wrappingKeyVersion; }
    byte[] wrappedDek() { return Arrays.copyOf(wrappedDek, wrappedDek.length); }
}
