package com.strangequark.odoc.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime controls for local-account/session authentication. */
@ConfigurationProperties("odoc.auth")
public record OdocAuthProperties(
        boolean localRegistrationEnabled,
        boolean inviteOnly,
        UUID instanceScopeId,
        Duration sessionTtl,
        boolean secureCookies,
        Duration freshAuthenticationTtl,
        int loginAccountAttemptLimit,
        int loginIpAttemptLimit,
        Duration loginRateLimitWindow,
        Duration loginRateLimitBlock,
        int invitationExchangeAttemptLimit,
        Duration invitationExchangeRateLimitWindow,
        Duration invitationExchangeRateLimitBlock) {
    public OdocAuthProperties {
        if (instanceScopeId == null) throw new IllegalArgumentException("instanceScopeId is required");
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
        if (freshAuthenticationTtl == null || freshAuthenticationTtl.isZero() || freshAuthenticationTtl.isNegative()) {
            throw new IllegalArgumentException("freshAuthenticationTtl must be positive");
        }
        if (loginAccountAttemptLimit < 1 || loginIpAttemptLimit < 1 || invitationExchangeAttemptLimit < 1) {
            throw new IllegalArgumentException("Login rate limits must be positive");
        }
        if (loginRateLimitWindow == null || loginRateLimitWindow.isZero() || loginRateLimitWindow.isNegative()
                || loginRateLimitBlock == null || loginRateLimitBlock.isZero() || loginRateLimitBlock.isNegative()) {
            throw new IllegalArgumentException("Login rate limit durations must be positive");
        }
        if (invitationExchangeRateLimitWindow == null || invitationExchangeRateLimitWindow.isZero()
                || invitationExchangeRateLimitWindow.isNegative() || invitationExchangeRateLimitBlock == null
                || invitationExchangeRateLimitBlock.isZero() || invitationExchangeRateLimitBlock.isNegative()) {
            throw new IllegalArgumentException("Invitation exchange rate limit durations must be positive");
        }
    }

    /** True only when a visitor may create an account without an invitation. */
    public boolean selfServiceRegistrationEnabled() {
        return localRegistrationEnabled && !inviteOnly;
    }
}
