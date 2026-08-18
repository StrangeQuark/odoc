package com.strangequark.odoc.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Local SMTP delivery adapter. Docker Compose routes it to Mailpit for safe inspection. */
@Service
@Profile("local")
public class AccountRecoveryMailService {
    private final JavaMailSender mailSender;

    AccountRecoveryMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordRecovery(String recipient, String verifier) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@odoc.local");
        message.setTo(recipient);
        message.setSubject("Reset your Odoc password");
        message.setText("Use this one-time Odoc password reset code:\n\n"
                + verifier
                + "\n\nIt expires in one hour. If you did not request a reset, you can ignore this email.");
        mailSender.send(message);
    }

    public void sendEmailVerification(String recipient, String verifier) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@odoc.local");
        message.setTo(recipient);
        message.setSubject("Verify your Odoc email address");
        message.setText("Use this one-time Odoc email verification code:\n\n"
                + verifier
                + "\n\nIt expires in 24 hours. If you did not create this account, you can ignore this email.");
        mailSender.send(message);
    }

    public void sendWorkspaceInvitation(String recipient, String workspaceName, java.util.UUID routeId, String verifier) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@odoc.local");
        message.setTo(recipient);
        message.setSubject("You were invited to " + workspaceName + " on Odoc");
        message.setText("Open this one-time Odoc workspace invitation link:\n\n"
                + "https://odoc.local/invitations/" + routeId + "#v=" + verifier
                + "\n\nThe secret verifier is kept in the fragment and is never sent in the request URL. "
                + "It expires in seven days. If you did not expect this invitation, you can ignore this email.");
        mailSender.send(message);
    }
}
