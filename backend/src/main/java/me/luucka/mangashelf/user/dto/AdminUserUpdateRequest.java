package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.NotNull;
import me.luucka.mangashelf.user.Role;

public record AdminUserUpdateRequest(
        @NotNull Role role,
        @NotNull Boolean enabled
) {
}
