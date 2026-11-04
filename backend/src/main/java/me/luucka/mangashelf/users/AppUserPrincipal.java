package me.luucka.mangashelf.users;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Stored in the HTTP session (JDK serialization), hence the snapshot fields. */
public class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

  private static final long serialVersionUID = 1L;

  private final UUID id;
  private final String username;
  private String passwordHash; // cleared after authentication, never stored in the session
  private final Role role;
  private final boolean mustChangePassword;

  public AppUserPrincipal(AppUser u) {
    this.id = u.getId();
    this.username = u.getUsername();
    this.passwordHash = u.getPasswordHash();
    this.role = u.getRole();
    this.mustChangePassword = u.isMustChangePassword();
  }

  public UUID id() {
    return id;
  }

  public Role role() {
    return role;
  }

  public boolean mustChangePassword() {
    return mustChangePassword;
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }

  @Override
  public String getUsername() {
    return username;
  }
}
