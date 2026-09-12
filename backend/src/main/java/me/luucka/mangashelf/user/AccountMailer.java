package me.luucka.mangashelf.user;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;

/** SMTP transport; links only use the configured origin, never request headers. */
@Component
public class AccountMailer {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String baseUrl;
    private final String from;

    public AccountMailer(JavaMailSender sender,
                         @Value("${app.email.enabled:false}") boolean enabled,
                         @Value("${app.email.base-url:}") String baseUrl,
                         @Value("${app.email.from:}") String from,
                         @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.from = from;
        if (enabled) {
            try {
                URI uri = URI.create(this.baseUrl);
                boolean localHttp = "http".equals(uri.getScheme())
                        && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
                if (!("https".equals(uri.getScheme()) || localHttp) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || !(uri.getPath().isEmpty()) || host.isBlank()
                        || from.contains("\r") || from.contains("\n")) {
                    throw new IllegalArgumentException();
                }
                new InternetAddress(from, true).validate();
            } catch (Exception ex) {
                throw new IllegalStateException("Email requires SMTP_HOST, a valid MAIL_FROM and an HTTPS APP_PUBLIC_URL origin");
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void send(AppUser user, String token, boolean verification) {
        boolean italian = user.getLanguage() == UiLanguage.IT;
        String link = baseUrl + (verification ? "/verify-email" : "/reset-password") + "#token=" + token;
        String subject = verification
                ? (italian ? "Conferma il tuo indirizzo email" : "Confirm your email address")
                : (italian ? "Reimposta la tua password" : "Reset your password");
        String instructions = verification
                ? (italian ? "Conferma il tuo indirizzo per accedere a MangaShelf. Il link scade tra 24 ore."
                : "Confirm your email address to sign in to MangaShelf. This link expires in 24 hours.")
                : (italian ? "Usa questo link per scegliere una nuova password. Il link scade tra 30 minuti."
                : "Use this link to choose a new password. This link expires in 30 minutes.");
        String footer = italian ? "Se non hai richiesto questa email, puoi ignorarla."
                : "If you did not request this email, you can ignore it.";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("MangaShelf — " + subject);
        message.setText(instructions + "\n\n" + link + "\n\n" + footer);
        sender.send(message);
    }
}
