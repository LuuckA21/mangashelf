package me.luucka.mangashelf.user.dto;

import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UiLanguage;

/** Public view of an account: never carries the password hash. */
public record UserResponse(Long id, String username, String email, Role role,
                           UiLanguage language) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(),
                user.getEmail(), user.getRole(), user.getLanguage());
    }
}
