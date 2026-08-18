package com.strangequark.odoc.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.jobs.OutboxPublisher;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AuditPublisherTest {
    @Mock private OutboxPublisher outbox;
    @Mock private ObjectProvider<jakarta.servlet.http.HttpServletRequest> request;

    @Test
    void recordsInstanceSecurityPolicyEventsWithoutAnActorOrSensitiveMetadata() {
        UUID instanceScopeId = UUID.randomUUID();
        when(request.getIfAvailable()).thenReturn(null);
        when(outbox.publish(any(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        new AuditPublisher(outbox, request).record(null, null, "security.login.rate_limited",
                "instance_security_policy", instanceScopeId, "blocked", "rate-limit-hmac-minute");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(null), eq("instance_security_policy"), eq(instanceScopeId), eq("audit.v1"),
                payload.capture(), eq("rate-limit-hmac-minute"));
        assertThat(payload.getValue()).containsEntry("action", "security.login.rate_limited")
                .containsEntry("outcome", "blocked")
                .doesNotContainKey("actorUserId")
                .doesNotContainKey("email")
                .doesNotContainKey("origin")
                .doesNotContainKey("credential");
    }
}
