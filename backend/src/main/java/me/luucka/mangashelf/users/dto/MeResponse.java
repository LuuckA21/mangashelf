package me.luucka.mangashelf.users.dto;

import me.luucka.mangashelf.users.AppUserPrincipal;
import me.luucka.mangashelf.users.Role;

public record MeResponse(String id, String username, Role role, boolean mustChangePassword) {

  public static MeResponse of(AppUserPrincipal p) {
    return new MeResponse(p.id().toString(), p.getUsername(), p.role(), p.mustChangePassword());
  }
}
