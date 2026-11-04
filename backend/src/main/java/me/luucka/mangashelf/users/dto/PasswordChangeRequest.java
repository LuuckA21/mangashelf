package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
    @NotBlank(message = "Campo obbligatorio") String currentPassword,
    @ValidPassword String newPassword) {}
