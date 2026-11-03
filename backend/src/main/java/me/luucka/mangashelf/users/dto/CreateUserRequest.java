package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import me.luucka.mangashelf.users.Role;

public record CreateUserRequest(
    @NotNull @Pattern(regexp = "[a-zA-Z0-9._-]{3,32}", message = "Username non valido")
        String username,
    @ValidPassword String temporaryPassword,
    @NotNull Role role) {}
