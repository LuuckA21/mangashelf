package me.luucka.mangashelf.users;

import java.time.Instant;
import java.util.UUID;
import me.luucka.mangashelf.common.ConflictException;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.users.dto.CreateUserRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final UserSessions sessions;

  public AdminUserService(AppUserRepository users, PasswordEncoder encoder, UserSessions sessions) {
    this.users = users;
    this.encoder = encoder;
    this.sessions = sessions;
  }

  @Transactional(readOnly = true)
  public Page<AppUser> list(Pageable pageable) {
    return users.findAll(pageable);
  }

  /** A duplicate username surfaces as a unique-index violation, which the API maps to 409. */
  public AppUser create(CreateUserRequest r) {
    return users.saveAndFlush(
        new AppUser(
            r.username(), encoder.encode(r.temporaryPassword()), r.role(), true, Instant.now()));
  }

  @Transactional
  public void resetPassword(UUID id, String temporaryPassword) {
    var user = find(id);
    user.resetPassword(encoder.encode(temporaryPassword));
    sessions.closeAll(user.getUsername());
  }

  @Transactional
  public AppUser changeRole(UUID actorId, UUID id, Role role) {
    if (actorId.equals(id)) {
      throw new ConflictException("Non puoi cambiare il tuo ruolo");
    }
    var user = find(id);
    if (user.getRole() == Role.ADMIN && role != Role.ADMIN) {
      requireAnotherAdmin();
    }
    user.changeRole(role);
    sessions.closeAll(user.getUsername());
    return user;
  }

  @Transactional
  public void delete(UUID actorId, UUID id) {
    if (actorId.equals(id)) {
      throw new ConflictException("Per eliminare il tuo account usa la pagina Account");
    }
    var user = find(id);
    if (user.getRole() == Role.ADMIN) {
      requireAnotherAdmin();
    }
    users.delete(user);
    sessions.closeAll(user.getUsername());
  }

  private AppUser find(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("Utente non trovato"));
  }

  private void requireAnotherAdmin() {
    if (users.countByRole(Role.ADMIN) <= 1) {
      throw new ConflictException("Non puoi rimuovere l'ultimo amministratore");
    }
  }
}
