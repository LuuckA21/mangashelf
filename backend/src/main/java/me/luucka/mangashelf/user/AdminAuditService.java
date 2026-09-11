package me.luucka.mangashelf.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes account audit events in the same transaction as the account change. */
@Service
public class AdminAuditService {

    private final AdminAuditEventRepository events;

    public AdminAuditService(AdminAuditEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<AdminAuditEvent> recentEvents() {
        return events.findTop100ByOrderByCreatedAtDescIdDesc();
    }

    public void recordRoleChange(UserPrincipal actor, AppUser target,
                                 Role oldRole, Role newRole) {
        record(actor, target, AdminAuditAction.ROLE_CHANGED,
                oldRole.name(), newRole.name());
    }

    public void recordStatusChange(UserPrincipal actor, AppUser target,
                                   boolean oldEnabled, boolean newEnabled) {
        record(actor, target, AdminAuditAction.STATUS_CHANGED,
                status(oldEnabled), status(newEnabled));
    }

    private void record(UserPrincipal actor, AppUser target,
                        AdminAuditAction action, String oldValue,
                        String newValue) {
        events.save(new AdminAuditEvent(actor, target, action, oldValue, newValue));
    }

    private String status(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }
}
