package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.luucka.mangashelf.user.UiLanguage;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "may only contain letters, digits, dot, dash and underscore")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        // Length is the only rule enforced here: composition requirements
        // push people towards predictable substitutions, whereas a long
        // passphrase is both easier to remember and harder to crack.
        @NotBlank @Size(min = 10, max = 200)
        String password,

        // Optional for backwards compatibility with older clients. When it
        // is absent the account starts in Italian.
        UiLanguage language
) {
}
