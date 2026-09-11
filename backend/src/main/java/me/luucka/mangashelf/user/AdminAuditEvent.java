package me.luucka.mangashelf.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.luucka.mangashelf.common.BaseEntity;

import java.time.Instant;

/**
 * Immutable application-level record of an administrator changing an account.
 *
 * <p>User ids keep the relationship queryable while the username snapshots
 * preserve a useful record if hard account deletion is introduced later.
 * This entity deliberately exposes no setters: events are inserted once and
 * never updated by application code.
 */
@Entity
@Table(name = "admin_audit_event")
@Getter
@NoArgsConstructor
public class AdminAuditEvent extends BaseEntity {

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 32)
    private String actorUsername;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_username", nullable = false, length = 32)
    private String targetUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminAuditAction action;

    @Column(name = "old_value", nullable = false, length = 32)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 32)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AdminAuditEvent(UserPrincipal actor, AppUser target,
                           AdminAuditAction action, String oldValue,
                           String newValue) {
        this.actorUserId = actor.id();
        this.actorUsername = actor.username();
        this.targetUserId = target.getId();
        this.targetUsername = target.getUsername();
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}
