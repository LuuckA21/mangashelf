package me.luucka.mangashelf.user;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;

/** SMTP transport; links only use the configured origin, never request headers. */
@Component
public class AccountMailer {
    private static final String TEMPLATE = loadTemplate();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z]+)\\}\\}");
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

    /** Security alerts contain neither enrollment secrets nor recovery codes. */
    public void sendSecurityNotice(String recipient, UiLanguage language, TwoFactorNotifications.Action action) {
        boolean it = language == UiLanguage.IT;
        String subject = switch (action) {
            case ENABLED -> it ? "Autenticazione a due fattori attivata" : "Two-factor authentication enabled";
            case DISABLED -> it ? "Autenticazione a due fattori disattivata" : "Two-factor authentication disabled";
            case RECOVERY_REGENERATED -> it ? "Codici di recupero 2FA rigenerati" : "2FA recovery codes regenerated";
        };
        String instructions = it ? "Le impostazioni di sicurezza del tuo account MangaShelf sono state modificate. Se non sei stato tu, cambia subito la password e contatta l’amministratore."
                : "Your MangaShelf account security settings changed. If this was not you, change your password immediately and contact your administrator.";
        String link = baseUrl + "/settings";
        String html = render(Map.of("language", it ? "it" : "en", "subject", subject,
                "preheader", subject, "eyebrow", "MangaShelf", "instructions", instructions,
                "button", it ? "Controlla account" : "Review account", "expiry", "",
                "link", link, "footer", it ? "Questo è un avviso di sicurezza automatico." : "This is an automatic security notice.",
                "fallback", it ? "Apri le impostazioni del tuo account:" : "Open your account settings:"), "MangaShelf");
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, "MangaShelf", StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject("MangaShelf — " + subject);
            helper.setText(subject + "\n\n" + instructions + "\n\n" + link, html);
            sender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailPreparationException("Unable to prepare security notice", ex);
        }
    }

    private enum Kind { VERIFICATION, RESET, DELETION }

    public void send(AppUser user, String token, boolean verification) {
        send(user, token, verification ? Kind.VERIFICATION : Kind.RESET);
    }

    public void sendDeletion(AppUser user, String token) {
        send(user, token, Kind.DELETION);
    }

    private void send(AppUser user, String token, Kind kind) {
        boolean italian = user.getLanguage() == UiLanguage.IT;
        boolean verification = kind == Kind.VERIFICATION;
        boolean deletion = kind == Kind.DELETION;
        String path = switch (kind) {
            case VERIFICATION -> "/verify-email";
            case RESET -> "/reset-password";
            case DELETION -> "/delete-account";
        };
        String link = baseUrl + path + "#token=" + token;
        String subject = deletion
                ? (italian ? "Conferma l’eliminazione del tuo account" : "Confirm your account deletion")
                : verification
                ? (italian ? "Conferma il tuo indirizzo email" : "Confirm your email address")
                : (italian ? "Reimposta la tua password" : "Reset your password");
        String instructions = deletion
                ? (italian ? "Hai richiesto di eliminare l’account " + user.getUsername() + " (" + user.getEmail()
                        + "). Apri il link per confermare: account, collezione e liste acquisti personali saranno eliminati definitivamente."
                : "You requested deletion of account " + user.getUsername() + " (" + user.getEmail()
                        + "). Open the link to confirm: your account, collection and personal purchase lists will be permanently deleted.")
                : verification
                ? (italian ? "La tua libreria ti aspetta. Conferma il tuo indirizzo email per iniziare a usare MangaShelf."
                : "Your library is waiting. Confirm your email address to get started with MangaShelf.")
                : (italian ? "Hai richiesto una nuova password per il tuo account MangaShelf. Scegline una nuova usando il pulsante qui sotto."
                : "You requested a new password for your MangaShelf account. Choose a new one using the button below.");
        String expiry = verification
                ? (italian ? "Questo link è monouso e scade tra 24 ore." : "This link can be used once and expires in 24 hours.")
                : (italian ? "Questo link è monouso e scade tra 30 minuti." : "This link can be used once and expires in 30 minutes.");
        String button = deletion
                ? (italian ? "Rivedi ed elimina account" : "Review account deletion")
                : verification
                ? (italian ? "Conferma email" : "Confirm email")
                : (italian ? "Reimposta password" : "Reset password");
        String footer = italian ? "Se non hai richiesto questa email, puoi ignorarla."
                : "If you did not request this email, you can ignore it.";
        String plain = subject + "\n\n" + instructions + "\n\n" + link + "\n\n" + expiry + "\n\n" + footer;
        String html = render(Map.of(
                "language", italian ? "it" : "en", "subject", subject,
                "preheader", subject + ". " + expiry,
                "eyebrow", verification ? (italian ? "Benvenuto nella tua libreria" : "Welcome to your library")
                        : (italian ? "Il tuo account" : "Your account"),
                "instructions", instructions, "button", button, "expiry", expiry,
                "link", link, "footer", footer,
                "fallback", italian ? "Se il pulsante non funziona, copia questo link nel browser:"
                        : "If the button does not work, copy this link into your browser:"),
                italian ? "La tua collezione, volume dopo volume." : "Your collection, one volume at a time.");
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, "MangaShelf", StandardCharsets.UTF_8.name()));
            helper.setTo(user.getEmail());
            helper.setSubject("MangaShelf — " + subject);
            helper.setText(plain, html);
            sender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailPreparationException("Unable to prepare account email", ex);
        }
    }

    private static String render(Map<String, String> values, String tagline) {
        // One pass prevents replacement text from becoming another placeholder.
        return PLACEHOLDER.matcher(TEMPLATE).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                HtmlUtils.htmlEscape(match.group(1).equals("tagline") ? tagline : values.get(match.group(1)))));
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource("mail/account-email.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load account email template", ex);
        }
    }
}
