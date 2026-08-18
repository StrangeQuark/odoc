package com.strangequark.odoc.auth;

import java.util.UUID;

/** The local cookie-session principal. The identifier is server-derived, never request input. */
public record AuthenticatedUser(UUID id, String email) {}
