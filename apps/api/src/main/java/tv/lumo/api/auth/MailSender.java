package tv.lumo.api.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * Sends the two transactional emails sprint 1 needs.
 *
 * <p>Both carry a single-use token in a link. <b>The token is never logged</b>,
 * not even at DEBUG (AGENTS.md §5): a verification link in a log file is a
 * working account takeover for anyone who reads that file.
 *
 * <p>Delivery failure does not fail the request. A registration that succeeds and
 * then 500s because the SMTP server blinked would leave the account created but
 * the caller told it was not — the worst of both outcomes. The user can ask for
 * another email; they cannot un-register.
 */
@Component
public class MailSender {

    private static final Logger log = LoggerFactory.getLogger(MailSender.class);

    private final JavaMailSender mailSender;
    private final LumoProperties properties;
    private final String from;

    public MailSender(JavaMailSender mailSender,
                      LumoProperties properties,
                      @org.springframework.beans.factory.annotation.Value("${LUMO_MAIL_FROM:no-reply@lumo.tv}")
                      String from) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.from = from;
    }

    public void sendEmailVerification(UserRow user, String token) {
        String link = properties.web().baseUrl() + "/verify-email?token=" + encode(token);
        send(user.email(), "Confirm your Lumo TV email address",
                "Open this link to confirm your email address:\n\n" + link + "\n\n"
                        + "The link is valid for 3 days and can be used once.",
                "email verification");
    }

    public void sendPasswordReset(UserRow user, String token) {
        String link = properties.web().baseUrl() + "/reset-password?token=" + encode(token);
        send(user.email(), "Reset your Lumo TV password",
                "Open this link to choose a new password:\n\n" + link + "\n\n"
                        + "The link is valid for 1 hour and can be used once. "
                        + "If you did not ask for this, you can ignore this message.",
                "password reset");
    }

    private void send(String to, String subject, String body, String kind) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Neither the recipient nor the link is logged: one identifies a user,
            // the other is a credential.
            log.warn("Failed to send a {} email: {}", kind, e.getClass().getSimpleName());
        }
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
