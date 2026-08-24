package com.strangequark.odoc.encryption;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ManagedDataKeyRepository extends JpaRepository<ManagedDataKey, UUID> {
    Optional<ManagedDataKey> findByScopeKindAndScopeIdAndPurposeAndKeyVersion(
            SecurityScopeKind scopeKind, UUID scopeId, EncryptionPurpose purpose, int keyVersion);

    Optional<ManagedDataKey> findByScopeKindAndScopeIdAndPurposeAndStatus(
            SecurityScopeKind scopeKind, UUID scopeId, EncryptionPurpose purpose, String status);

    java.util.List<ManagedDataKey> findByScopeKindAndScopeIdAndPurposeOrderByKeyVersionDesc(
            SecurityScopeKind scopeKind, UUID scopeId, EncryptionPurpose purpose);
}
