package com.strangequark.odoc.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Narrow adapter so invitation controllers never learn the rate-limit storage/key details. */
@Service
@Profile("local")
public class InvitationRateLimitService {
    private final AuthRateLimitService delegate;

    InvitationRateLimitService(AuthRateLimitService delegate) {
        this.delegate = delegate;
    }

    public void assertPermitted(String origin) { delegate.assertInvitationExchangePermitted(origin); }
    public void recordFailure(String origin) { delegate.recordInvitationExchangeFailure(origin); }
    public void recordSuccess(String origin) { delegate.recordInvitationExchangeSuccess(origin); }
}
