package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Explicit credential confirmation for the destructive self-service operation. */
public record DeleteAccountRequest(
        @NotBlank @Size(max = 200) String currentPassword
) {
}
