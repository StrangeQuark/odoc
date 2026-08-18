package com.strangequark.odoc.system;

import java.time.Instant;

/** Non-sensitive sample response used to prove the generated API contract path. */
public record SystemInfoResponse(String name, String status, Instant timestamp) {}
