package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.dto.AdminUserUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final AppUserRepository users;
    private final AccountAdministration administration;
    private final AdminAuditService audit;

    public AdminUserService(AppUserRepository users, AccountAdministration administration,
                            AdminAuditService audit) {
        this.users = users;
        this.administration = administration;
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
        administration.lock();
        // A request may have passed the security filter before waiting for
        // another administrator to revoke its actor. Recheck under the same
        // lock as role/status changes and deletion, before touching a target.
        AppUser actor = users.findByIdForUpdate(principal.id()).orElse(null);
        if (actor == null || !actor.isEnabled() || !actor.isEmailVerified()
                || actor.getRole() != Role.ADMIN
                || actor.getSessionVersion() != principal.sessionVersion()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "session_invalid");
        }
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
        if (removesEnabledAdmin) {
            administration.requireAnotherAdministrator(user);
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

}
