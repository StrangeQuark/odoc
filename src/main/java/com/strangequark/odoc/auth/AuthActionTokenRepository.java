package com.strangequark.odoc.auth;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, UUID> {
    Optional<AuthActionToken> findByTokenHash(byte[] tokenHash);
    List<AuthActionToken> findAllByUserIdAndActionTypeAndConsumedAtIsNull(UUID userId, AuthActionType actionType);
}
