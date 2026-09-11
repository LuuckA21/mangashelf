package me.luucka.mangashelf.user;

import jakarta.persistence.EntityManager;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.dto.AdminUserUpdateRequest;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    /** ASCII "MANGAUSR"; serialises changes that could remove administrators. */
    private static final long ADMIN_UPDATE_LOCK_KEY = 0x4D414E4741555352L;

    private final AppUserRepository users;
    private final EntityManager entityManager;
    private final AdminAuditService audit;

    public AdminUserService(AppUserRepository users, EntityManager entityManager,
                            AdminAuditService audit) {
        this.users = users;
        this.entityManager = entityManager;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppUser> listUsers() {
        return users.findAllByOrderByUsernameAsc();
    }

    /**
     * Changes role and enabled state while preserving at least one enabled
     * administrator. PostgreSQL's transaction advisory lock closes the race
     * where two administrators disable or demote each other simultaneously.
     */
    @Transactional
    public AppUser updateUser(Long userId, AdminUserUpdateRequest request,
                              UserPrincipal principal) {
        lockAdministration();
        AppUser user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        boolean roleChanged = user.getRole() != request.role();
        boolean enabledChanged = user.isEnabled() != request.enabled();
        if (!roleChanged && !enabledChanged) {
            return user;
        }
        if (user.getId().equals(principal.id())) {
            throw ApiException.conflict("cannot_modify_self");
        }

        boolean removesEnabledAdmin = user.isEnabled()
                && user.getRole() == Role.ADMIN
                && (!request.enabled() || request.role() != Role.ADMIN);
        if (removesEnabledAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("last_admin_required");
        }

        Role oldRole = user.getRole();
        boolean oldEnabled = user.isEnabled();
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        user.setSessionVersion(user.getSessionVersion() + 1);

        // The audit inserts share this transaction. A database failure cannot
        // leave behind an unrecorded authorisation change, or vice versa.
        if (roleChanged) {
            audit.recordRoleChange(principal, user, oldRole, request.role());
        }
        if (enabledChanged) {
            audit.recordStatusChange(principal, user, oldEnabled, request.enabled());
        }
        return user;
    }

    private void lockAdministration() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, ADMIN_UPDATE_LOCK_KEY);
                statement.execute();
            }
        });
    }
}
