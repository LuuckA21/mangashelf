package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identity fields a signed-in user may change after confirming the password. */
public record ProfileUpdateRequest(

        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "may only contain letters, digits, dot, dash and underscore")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(max = 200)
        String currentPassword
) {
}
