package me.luucka.mangashelf.user;

import jakarta.persistence.EntityManager;
import me.luucka.mangashelf.common.ApiException;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** Shared transaction lock for role/status changes and account deletion. */
@Component
public class AccountAdministration {
    private static final long LOCK_KEY = 0x4D414E4741555352L;
    private final EntityManager entityManager;
    private final AppUserRepository users;

    public AccountAdministration(EntityManager entityManager, AppUserRepository users) {
        this.entityManager = entityManager;
        this.users = users;
    }

    // Always acquire before user-row locks, inside the caller's transaction.
    public void lock() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                statement.execute();
            }
        });
    }

    public void requireAnotherAdministrator(AppUser user) {
        if (user.getRole() == Role.ADMIN && user.isEnabled() && user.isEmailVerified()
                && users.countByRoleAndEnabledTrueAndEmailVerifiedTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("last_admin_required");
        }
    }
}
