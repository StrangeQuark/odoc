package com.strangequark.odoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OdocSecurityPropertiesTest {
    @Test
    void onlyReturnsExplicitlyConfiguredCorsOrigins() {
        OdocSecurityProperties policy = new OdocSecurityProperties(List.of("https://docs.example"), true);

        assertThat(policy.allows("https://docs.example")).isTrue();
        assertThat(policy.allows("https://evil.example")).isFalse();
        assertThat(policy.rejectForwardedHeaders()).isTrue();
    }
}
