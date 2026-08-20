package me.luucka.mangashelf.user;

import jakarta.persistence.EntityManager;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.config.AppProperties;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import org.hibernate.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

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

        // The first account to exist becomes the administrator, so a fresh
        // instance does not need a seeded password in the compose file.
        if (users.count() == 0) {
            user.setRole(Role.ADMIN);
        }

        return users.save(user);
    }
}
