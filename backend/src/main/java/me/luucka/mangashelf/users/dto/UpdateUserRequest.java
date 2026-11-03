package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotNull;
import me.luucka.mangashelf.users.Role;

public record UpdateUserRequest(@NotNull Role role) {}
