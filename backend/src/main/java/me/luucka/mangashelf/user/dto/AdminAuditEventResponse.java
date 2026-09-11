package me.luucka.mangashelf.user.dto;

import me.luucka.mangashelf.user.AdminAuditAction;
import me.luucka.mangashelf.user.AdminAuditEvent;

import java.time.Instant;

public record AdminAuditEventResponse(
        Long id,
        Long actorUserId,
        String actorUsername,
        Long targetUserId,
        String targetUsername,
        AdminAuditAction action,
        String oldValue,
        String newValue,
        Instant createdAt
) {
    public static AdminAuditEventResponse from(AdminAuditEvent event) {
        return new AdminAuditEventResponse(
                event.getId(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getTargetUserId(),
                event.getTargetUsername(),
                event.getAction(),
                event.getOldValue(),
                event.getNewValue(),
                event.getCreatedAt());
    }
}
