package com.strangequark.odoc.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface AuthSecurityEventRepository extends JpaRepository<AuthSecurityEvent, UUID> {}
