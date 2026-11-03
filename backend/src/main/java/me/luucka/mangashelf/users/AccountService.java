package me.luucka.mangashelf.users;

import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.common.ApiExceptionHandler;
import me.luucka.mangashelf.common.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class AccountService {

  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final UserSessions sessions;

  public AccountService(AppUserRepository users, PasswordEncoder encoder, UserSessions sessions) {
    this.users = users;
    this.encoder = encoder;
    this.sessions = sessions;
  }

  /** Deletes the account and closes all its sessions, including the current one. */
  @Transactional
  public void delete(UUID userId, String password) {
    var user = users.findById(userId).orElseThrow();
    if (!encoder.matches(password, user.getPasswordHash())) {
      var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
      problem.setProperty(
          "errors", List.of(new ApiExceptionHandler.FieldError("password", "Password errata")));
      throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
    if (user.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
      throw new ConflictException("Non puoi eliminare l'ultimo amministratore");
    }
    users.delete(user);
    sessions.closeAll(user.getUsername());
  }
}
