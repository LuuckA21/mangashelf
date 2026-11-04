package me.luucka.mangashelf.users.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank(message = "Campo obbligatorio") String password) {}
