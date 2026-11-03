package me.luucka.mangashelf.users.dto;

import java.time.Instant;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.Role;

public record UserResponse(
    String id, String username, Role role, boolean mustChangePassword, Instant createdAt) {

  public static UserResponse of(AppUser u) {
    return new UserResponse(
        u.getId().toString(),
        u.getUsername(),
        u.getRole(),
        u.isMustChangePassword(),
        u.getCreatedAt());
  }
}
