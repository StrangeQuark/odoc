package com.strangequark.odoc.encryption;

import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Creates and retrieves purpose-separated DEKs, storing only their wrapped form in PostgreSQL. */
class PersistentDataEncryptionKeyProvider implements DataEncryptionKeyProvider {
    private static final String ACTIVE = "ACTIVE";

    private final ManagedDataKeyRepository repository;
    private final KeyWrappingProvider wrappingProvider;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    PersistentDataEncryptionKeyProvider(
            ManagedDataKeyRepository repository, KeyWrappingProvider wrappingProvider, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.wrappingProvider = wrappingProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public DataEncryptionKey activeKey(SecurityScope scope, EncryptionPurpose purpose) {
        ManagedDataKey existing = repository
                .findByScopeKindAndScopeIdAndPurposeAndStatus(scope.kind(), scope.id(), purpose, ACTIVE)
                .orElse(null);
        if (existing != null) {
            return unwrap(existing);
        }

        // Serialize first-key creation across API replicas. A collision merely serializes work;
        // it cannot cause two active keys for the same scope and purpose.
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", result -> null, lockName(scope, purpose));
        return repository
                .findByScopeKindAndScopeIdAndPurposeAndStatus(scope.kind(), scope.id(), purpose, ACTIVE)
                .map(this::unwrap)
                .orElseGet(() -> create(scope, purpose));
    }

    @Override
    @Transactional(readOnly = true)
    public DataEncryptionKey key(SecurityScope scope, EncryptionPurpose purpose, int version) {
        ManagedDataKey row = repository
                .findByScopeKindAndScopeIdAndPurposeAndKeyVersion(scope.kind(), scope.id(), purpose, version)
                .orElseThrow(() -> new ManagedEncryptionException("Unable to decrypt the record.", null));
        return unwrap(row);
    }

    @Override
    @Transactional
    public DataEncryptionKey rotate(SecurityScope scope, EncryptionPurpose purpose) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", result -> null, lockName(scope, purpose));
        ManagedDataKey current = repository
                .findByScopeKindAndScopeIdAndPurposeAndStatus(scope.kind(), scope.id(), purpose, ACTIVE)
                .orElseThrow(() -> new ManagedEncryptionException("No active encryption key exists.", null));
        int nextVersion = repository.findByScopeKindAndScopeIdAndPurposeOrderByKeyVersionDesc(
                scope.kind(), scope.id(), purpose).stream().mapToInt(ManagedDataKey::keyVersion).max().orElse(0) + 1;
        current.retire();
        byte[] rawDek = new byte[32];
        secureRandom.nextBytes(rawDek);
        SecretKey dek = new SecretKeySpec(rawDek, "AES");
        ManagedDataKey created = repository.saveAndFlush(new ManagedDataKey(scope, purpose, nextVersion,
                wrappingProvider.wrappingKeyVersion(), wrappingProvider.wrap(dek), Instant.now()));
        return new DataEncryptionKey(created.keyVersion(), dek);
    }

    @Override
    @Transactional
    public void disable(SecurityScope scope, EncryptionPurpose purpose, int version) {
        ManagedDataKey row = repository.findByScopeKindAndScopeIdAndPurposeAndKeyVersion(
                scope.kind(), scope.id(), purpose, version)
                .orElseThrow(() -> new ManagedEncryptionException("Encryption key does not exist.", null));
        if (row.isActive()) {
            throw new ManagedEncryptionException("An active key cannot be disabled before rotation.", null);
        }
        row.disable();
    }

    private DataEncryptionKey create(SecurityScope scope, EncryptionPurpose purpose) {
        byte[] rawDek = new byte[32];
        secureRandom.nextBytes(rawDek);
        SecretKey dek = new SecretKeySpec(rawDek, "AES");
        ManagedDataKey created = repository.saveAndFlush(new ManagedDataKey(
                scope,
                purpose,
                1,
                wrappingProvider.wrappingKeyVersion(),
                wrappingProvider.wrap(dek),
                Instant.now()));
        return new DataEncryptionKey(created.keyVersion(), dek);
    }

    private DataEncryptionKey unwrap(ManagedDataKey row) {
        return new DataEncryptionKey(
                row.keyVersion(), wrappingProvider.unwrap(row.wrappingKeyVersion(), row.wrappedDek()));
    }

    private static String lockName(SecurityScope scope, EncryptionPurpose purpose) {
        return scope.kind() + ":" + scope.id() + ":" + purpose;
    }
}
