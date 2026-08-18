package com.strangequark.odoc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryMailServiceTest {

    @Mock private JavaMailSender mailSender;

    @Test
    void sendsTheEmailVerificationCodeOnlyInTheRecipientMessage() {
        AccountRecoveryMailService service = new AccountRecoveryMailService(mailSender);

        service.sendEmailVerification("member@example.test", "one-time-verifier");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("member@example.test");
        assertThat(message.getValue().getSubject()).isEqualTo("Verify your Odoc email address");
        assertThat(message.getValue().getText()).contains("one-time-verifier").contains("24 hours");
    }
}
