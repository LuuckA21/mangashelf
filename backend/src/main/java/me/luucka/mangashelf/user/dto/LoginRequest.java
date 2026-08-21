package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param login username or email */
public record LoginRequest(
        @NotBlank @Size(max = 255) String login,
        @NotBlank @Size(max = 200) String password) {
}
