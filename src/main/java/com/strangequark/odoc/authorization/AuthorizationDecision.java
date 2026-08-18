package com.strangequark.odoc.authorization;

/** A content-free decision that may be audit-recorded without resource titles or bodies. */
public record AuthorizationDecision(boolean allowed, String reason) {
    static AuthorizationDecision allow(String reason) { return new AuthorizationDecision(true, reason); }
    static AuthorizationDecision deny(String reason) { return new AuthorizationDecision(false, reason); }
}
