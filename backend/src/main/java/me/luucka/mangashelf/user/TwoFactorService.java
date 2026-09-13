package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TwoFactorService {
    private final AppUserRepository users;
    private final TwoFactorCipher cipher;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    // Independent of password-login counters: knowing the password must not reset MFA failures.
    private final LoginAttempts attempts = new LoginAttempts();

    public TwoFactorService(AppUserRepository users, TwoFactorCipher cipher,
                            PasswordEncoder passwords, JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.users = users;
        this.cipher = cipher;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.events = events;
    }

    public record Status(boolean available, boolean enabled, int recoveryCodesRemaining) {}
    public record Setup(String secret, String uri) {
        @Override public String toString() { return "Setup[redacted]"; }
    }
    public record Recovery(AppUser account, List<String> codes) {
        @Override public String toString() { return "Recovery[redacted]"; }
    }

    @Transactional(readOnly = true)
    public Status status(UserPrincipal principal) {
        AppUser user = users.findById(principal.id()).orElseThrow(TwoFactorService::invalidSession);
        return new Status(cipher.available(), user.getTwoFactorSecret() != null,
                jdbc.queryForObject("SELECT count(*) FROM two_factor_recovery_code WHERE user_id = ?",
                        Integer.class, user.getId()));
    }

    /** Also rejects credentials verified just before a password/role/MFA change. */
    @Transactional
    public AppUser current(UserPrincipal principal) { return locked(principal); }

    private AppUser locked(UserPrincipal principal) {
        AppUser user = users.findByIdForUpdate(principal.id()).orElseThrow(TwoFactorService::invalidSession);
        if (!user.isEnabled() || !user.isEmailVerified()
                || user.getSessionVersion() != principal.sessionVersion()
                || user.getRole() != principal.role()) throw invalidSession();
        return user;
    }

    public LoginAttempts.Attempt reserve(long userId, String address) {
        var permit = attempts.tryAcquire("user:" + userId, address);
        if (permit == null) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts");
        return permit;
    }

    @Transactional
    public Setup setup(UserPrincipal principal, String password) {
        cipher.requireAvailable();
        AppUser user = locked(principal);
        if (user.getTwoFactorSecret() != null) throw ApiException.conflict("two_factor_already_enabled");
        checkPassword(user, password);
        byte[] secret = Totp.newSecret();
        user.setTwoFactorPendingSecret(cipher.encrypt(user.getId(), secret));
        user.setTwoFactorPendingExpiresAt(Instant.now().plusSeconds(600));
        user.setTwoFactorPendingVersion(user.getSessionVersion());
        String base32 = Totp.base32(secret);
        String label = URLEncoder.encode("MangaShelf:" + user.getUsername(), StandardCharsets.UTF_8).replace("+", "%20");
        return new Setup(base32, "otpauth://totp/" + label + "?secret=" + base32
                + "&issuer=MangaShelf&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional
    public Recovery enable(UserPrincipal principal, String code) {
        AppUser user = locked(principal);
        if (user.getTwoFactorSecret() != null) throw ApiException.conflict("two_factor_already_enabled");
        if (user.getTwoFactorPendingSecret() == null || user.getTwoFactorPendingExpiresAt() == null
                || !user.getTwoFactorPendingExpiresAt().isAfter(Instant.now())
                || !Objects.equals(user.getTwoFactorPendingVersion(), user.getSessionVersion())) {
            throw ApiException.badRequest("two_factor_setup_expired");
        }
        long step = Totp.match(cipher.decrypt(user.getId(), user.getTwoFactorPendingSecret()),
                code, Instant.now().getEpochSecond(), -1);
        if (step < 0) throw invalidCode();
        user.setTwoFactorSecret(user.getTwoFactorPendingSecret());
        user.setTwoFactorLastStep(step);
        clearPending(user);
        user.setSessionVersion(user.getSessionVersion() + 1);
        var codes = replaceRecovery(user.getId());
        notify(user, TwoFactorNotifications.Action.ENABLED);
        return new Recovery(user, codes);
    }

    @Transactional
    public void cancelSetup(UserPrincipal principal) { clearPending(locked(principal)); }

    @Transactional
    public AppUser verifyLogin(UserPrincipal principal, String code) {
        AppUser user = locked(principal);
        if (user.getTwoFactorSecret() == null) throw invalidSession();
        verifyFactor(user, code);
        return user;
    }

    @Transactional
    public AppUser disable(UserPrincipal principal, String password, String code) {
        AppUser user = locked(principal);
        requireEnabled(user);
        checkCredentials(user, password, code);
        user.setTwoFactorSecret(null);
        user.setTwoFactorLastStep(-1);
        clearPending(user);
        jdbc.update("DELETE FROM two_factor_recovery_code WHERE user_id = ?", user.getId());
        user.setSessionVersion(user.getSessionVersion() + 1);
        notify(user, TwoFactorNotifications.Action.DISABLED);
        return user;
    }

    @Transactional
    public Recovery regenerate(UserPrincipal principal, String password, String code) {
        AppUser user = locked(principal);
        requireEnabled(user);
        checkCredentials(user, password, code);
        user.setSessionVersion(user.getSessionVersion() + 1);
        var codes = replaceRecovery(user.getId());
        notify(user, TwoFactorNotifications.Action.RECOVERY_REGENERATED);
        return new Recovery(user, codes);
    }

    /** Caller owns the user row lock and transaction; failed later writes roll back code consumption. */
    public void checkCredentials(AppUser user, String password, String code) {
        checkPassword(user, password);
        if (user.getTwoFactorSecret() != null) verifyFactor(user, code);
    }

    private void checkPassword(AppUser user, String password) {
        if (password == null || AuthService.passwordTooLong(password)
                || !passwords.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("current_password_invalid");
        }
    }

    private void verifyFactor(AppUser user, String code) {
        // Key loss must never silently disable MFA, including the recovery path.
        cipher.requireAvailable();
        if (code != null && code.matches("[0-9]{6}")) {
            long step = Totp.match(cipher.decrypt(user.getId(), user.getTwoFactorSecret()), code,
                    Instant.now().getEpochSecond(), user.getTwoFactorLastStep());
            if (step < 0) throw invalidCode();
            user.setTwoFactorLastStep(step);
            return;
        }
        String normalized = code == null ? "" : code.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{32}") || jdbc.update(
                "DELETE FROM two_factor_recovery_code WHERE user_id = ? AND code_hash = ?",
                user.getId(), AccountEmailService.hash(normalized)) != 1) throw invalidCode();
    }

    private List<String> replaceRecovery(long id) {
        jdbc.update("DELETE FROM two_factor_recovery_code WHERE user_id = ?", id);
        List<String> result = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String raw = HexFormat.of().formatHex(bytes);
            jdbc.update("INSERT INTO two_factor_recovery_code(user_id, code_hash) VALUES (?, ?)",
                    id, AccountEmailService.hash(raw));
            result.add(raw.substring(0, 8) + "-" + raw.substring(8, 16) + "-"
                    + raw.substring(16, 24) + "-" + raw.substring(24));
        }
        return List.copyOf(result);
    }

    private static void clearPending(AppUser user) {
        user.setTwoFactorPendingSecret(null);
        user.setTwoFactorPendingExpiresAt(null);
        user.setTwoFactorPendingVersion(null);
    }
    private void notify(AppUser user, TwoFactorNotifications.Action action) {
        events.publishEvent(new TwoFactorNotifications.Change(user.getEmail(), user.getLanguage(), action));
    }
    private static void requireEnabled(AppUser user) {
        if (user.getTwoFactorSecret() == null) throw ApiException.conflict("two_factor_not_enabled");
    }
    private static ApiException invalidCode() { return ApiException.badRequest("two_factor_invalid"); }
    private static ApiException invalidSession() { return new ApiException(HttpStatus.UNAUTHORIZED, "session_invalid"); }
}
