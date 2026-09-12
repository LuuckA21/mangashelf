package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class AccountEmailService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AccountMailer mailer;
    private final SecureRandom random = new SecureRandom();

    public AccountEmailService(AppUserRepository users, PasswordEncoder encoder, AccountMailer mailer) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
    }

    // Called inside the registration transaction: SMTP failure rolls the account back.
    public void prepareRegistration(AppUser user) {
        if (!mailer.enabled()) return;
        user.setEmailVerified(false);
        try {
            issue(user, true);
        } catch (MailException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_unavailable");
        }
    }

    /** Runs in a bounded background worker so responses do not reveal account existence by SMTP timing. */
    @Transactional
    public void requestEmail(String email, boolean verification) {
        if (!mailer.enabled()) return;
        // Find the id first, then load the entity under the same lock used by consumption/password changes.
        Long id = users.findIdByEmail(email).orElse(null);
        if (id == null) return;
        AppUser user = users.findByIdForUpdate(id).orElse(null);
        if (user == null || !user.isEnabled() || verification == user.isEmailVerified()) return;
        issue(user, verification);
    }

    private void issue(AppUser user, boolean verification) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (verification) {
            user.setVerificationHash(hash(token));
            user.setVerificationExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        } else {
            user.setResetHash(hash(token));
            user.setResetExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
            user.setResetSessionVersion(user.getSessionVersion());
        }
        // Only hashes enter the database. Failed delivery rolls back the replacement token.
        users.flush();
        mailer.send(user, token, verification);
    }

    @Transactional
    public void verify(String token) {
        String digest = hash(token);
        Long id = users.findIdByVerificationHash(digest).orElseThrow(AccountEmailService::invalidToken);
        AppUser user = users.findByIdForUpdate(id).orElseThrow(AccountEmailService::invalidToken);
        if (!user.isEnabled() || user.isEmailVerified()
                || !digest.equals(user.getVerificationHash()) || expired(user.getVerificationExpiresAt())) {
            throw invalidToken();
        }
        user.setEmailVerified(true);
        user.setVerificationHash(null);
        user.setVerificationExpiresAt(null);
    }

    @Transactional
    public void reset(String token, String password) {
        if (AuthService.passwordTooLong(password)) throw ApiException.badRequest("password_too_long");
        String digest = hash(token);
        Long id = users.findIdByResetHash(digest).orElseThrow(AccountEmailService::invalidToken);
        AppUser user = users.findByIdForUpdate(id).orElseThrow(AccountEmailService::invalidToken);
        if (!user.isEnabled() || !user.isEmailVerified() || !digest.equals(user.getResetHash())
                || expired(user.getResetExpiresAt())
                || !Objects.equals(user.getResetSessionVersion(), user.getSessionVersion())) {
            throw invalidToken();
        }
        if (encoder.matches(password, user.getPasswordHash())) throw ApiException.badRequest("password_unchanged");
        user.setPasswordHash(encoder.encode(password));
        user.setSessionVersion(user.getSessionVersion() + 1);
        user.setResetHash(null);
        user.setResetExpiresAt(null);
        user.setResetSessionVersion(null);
    }

    private static boolean expired(Instant expiry) {
        return expiry == null || !expiry.isAfter(Instant.now());
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("email_token_invalid");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
