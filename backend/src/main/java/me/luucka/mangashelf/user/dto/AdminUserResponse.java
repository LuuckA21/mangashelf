package me.luucka.mangashelf.user.dto;

import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.Role;

import java.time.Instant;

/** Administrative account view; credentials and session markers stay private. */
public record AdminUserResponse(
        Long id,
        String username,
        String email,
        Role role,
        boolean enabled,
        String language,
        Instant createdAt
) {
    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getLanguage().code(),
                user.getCreatedAt());
    }
}
