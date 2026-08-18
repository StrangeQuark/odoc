package com.strangequark.odoc.auth;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByTokenHash(byte[] tokenHash);
    List<AuthSession> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
