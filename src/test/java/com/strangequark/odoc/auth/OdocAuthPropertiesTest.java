package com.strangequark.odoc.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OdocAuthPropertiesTest {
    @Test
    void inviteOnlyDisablesUninvitedSelfServiceRegistration() {
        assertFalse(properties(true, true).selfServiceRegistrationEnabled());
    }

    @Test
    void selfServiceRegistrationRequiresTheLocalRegistrationCapability() {
        assertTrue(properties(true, false).selfServiceRegistrationEnabled());
        assertFalse(properties(false, false).selfServiceRegistrationEnabled());
    }

    private static OdocAuthProperties properties(boolean localRegistrationEnabled, boolean inviteOnly) {
        return new OdocAuthProperties(
                localRegistrationEnabled,
                inviteOnly,
                UUID.randomUUID(),
                Duration.ofHours(8),
                false,
                Duration.ofMinutes(15),
                5,
                20,
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                20,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15));
    }
}
