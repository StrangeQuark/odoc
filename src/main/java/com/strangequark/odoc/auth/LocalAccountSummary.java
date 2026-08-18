package com.strangequark.odoc.auth;

import java.util.UUID;

/** Minimal server-derived account identity shared with local membership administration. */
public record LocalAccountSummary(UUID id, String email) {}
