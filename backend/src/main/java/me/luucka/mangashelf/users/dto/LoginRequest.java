package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Campo obbligatorio") String username, @NotBlank String password) {}
