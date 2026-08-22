package me.luucka.mangashelf.user;

import jakarta.persistence.EntityManager;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.config.AppProperties;
import me.luucka.mangashelf.user.dto.PasswordChangeRequest;
import me.luucka.mangashelf.user.dto.ProfileUpdateRequest;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import org.hibernate.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    /** ASCII "MANGASHE"; advisory locks are scoped to this database. */
    private static final long REGISTRATION_LOCK_KEY = 0x4D414E4741534845L;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties properties;
    private final EntityManager entityManager;

    public AuthService(AppUserRepository users, PasswordEncoder encoder,
                       AppProperties properties, EntityManager entityManager) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    /**
     * Creates an account, or fails if registration is closed or the name or
     * email is taken.
     *
     * <p>Registrations are serialized around the uniqueness checks and the
     * first-admin election. The schema constraints remain the final guarantee;
     * these checks turn collisions into readable API errors.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        if (!properties.registrationEnabled()) {
            throw ApiException.forbidden("registration_closed");
        }

        if (passwordTooLong(request.password())) {
            throw ApiException.badRequest("password_too_long");
        }

        // BCrypt is deliberately expensive. Do it before taking the database
        // lock so simultaneous registrations wait only for the short section
        // that decides which account is first and writes the row.
        String passwordHash = encoder.encode(request.password());

        // Locking in PostgreSQL, rather than synchronizing this Java object,
        // also works when two backend instances serve the same database. The
        // transaction releases the lock automatically on commit or rollback.
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, REGISTRATION_LOCK_KEY);
                statement.execute();
            }
        });

        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("username_taken");
        }
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("email_taken");
        }

        AppUser user = new AppUser(
                request.username(),
                request.email().toLowerCase(Locale.ROOT),
                passwordHash);
        user.setLanguage(request.language() == null ? UiLanguage.IT : request.language());

        // The first account to exist becomes the administrator, so a fresh
        // instance does not need a seeded password in the compose file.
        if (users.count() == 0) {
            user.setRole(Role.ADMIN);
        }

        return users.save(user);
    }

    @Transactional
    public AppUser updateLanguage(Long userId, UiLanguage language) {
        AppUser user = load(userId);
        user.setLanguage(language);
        return user;
    }

    /** Changes the public identity only after re-authenticating the session holder. */
    @Transactional
    public AppUser updateProfile(Long userId, ProfileUpdateRequest request) {
        AppUser user = load(userId);
        requireCurrentPassword(user, request.currentPassword());

        if (users.existsByUsernameIgnoreCaseAndIdNot(request.username(), userId)) {
            throw ApiException.conflict("username_taken");
        }

        String email = request.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw ApiException.conflict("email_taken");
        }

        user.setUsername(request.username());
        user.setEmail(email);
        return user;
    }

    /** Rotates the BCrypt hash without ever returning either password. */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        AppUser user = load(userId);
        requireCurrentPassword(user, request.currentPassword());
        if (passwordTooLong(request.newPassword())) {
            throw ApiException.badRequest("password_too_long");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
    }

    /**
     * Removes a regular account and its personal rows through the schema's
     * cascades. Administrators must never self-delete: an authenticated
     * session retains its authorities until it expires, so deleting an admin
     * here could leave a privileged orphan session and an instance with no
     * account able to maintain the shared catalogue.
     */
    @Transactional
    public void deleteAccount(Long userId, String currentPassword) {
        AppUser user = load(userId);
        requireCurrentPassword(user, currentPassword);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("admin_account_delete_forbidden");
        }
        users.delete(user);
    }

    private AppUser load(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found"));
    }

    private void requireCurrentPassword(AppUser user, String currentPassword) {
        if (passwordTooLong(currentPassword)
                || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("current_password_invalid");
        }
    }

    static boolean passwordTooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES;
    }
}
