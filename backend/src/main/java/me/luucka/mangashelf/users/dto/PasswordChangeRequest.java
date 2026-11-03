package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
    @NotBlank String currentPassword, @Size(min = 12, max = 64) String newPassword) {}
