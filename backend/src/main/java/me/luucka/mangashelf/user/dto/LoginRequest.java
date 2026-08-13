package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.NotBlank;

/** @param login username or email */
public record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
