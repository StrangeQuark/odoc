package com.strangequark.odoc.workspace;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface WorkspaceInvitationCapabilityRepository extends JpaRepository<WorkspaceInvitationCapability, java.util.UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select capability from WorkspaceInvitationCapability capability where capability.tokenHash = :tokenHash")
    Optional<WorkspaceInvitationCapability> findByTokenHashForUpdate(byte[] tokenHash);
}
