package me.luucka.mangashelf.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import me.luucka.mangashelf.common.ApiExceptionHandler;
import me.luucka.mangashelf.users.dto.LoginRequest;
import me.luucka.mangashelf.users.dto.MeResponse;
import me.luucka.mangashelf.users.dto.PasswordChangeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository contextRepository =
      new HttpSessionSecurityContextRepository();

  private final CsrfTokenRepository csrfTokenRepository;
  private final CsrfLogoutHandler csrfLogout;

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final UserSessions userSessions;

  public AuthController(
      AuthenticationManager authenticationManager,
      CsrfTokenRepository csrfTokenRepository,
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      UserSessions userSessions) {
    this.authenticationManager = authenticationManager;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.userSessions = userSessions;
    this.csrfTokenRepository = csrfTokenRepository;
    this.csrfLogout = new CsrfLogoutHandler(csrfTokenRepository);
  }

  @PostMapping("/login")
  public MeResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var auth =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
    if (request.getSession(false) != null) {
      request.changeSessionId(); // session fixation protection
    }
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    return MeResponse.of((AppUserPrincipal) auth.getPrincipal());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    csrfLogout.logout(request, response, null);
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AppUserPrincipal me) {
    return MeResponse.of(me);
  }

  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal AppUserPrincipal me,
      @Valid @RequestBody PasswordChangeRequest body) {
    var user = users.findById(me.id()).orElseThrow();
    if (!passwordEncoder.matches(body.currentPassword(), user.getPasswordHash())) {
      var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
      problem.setProperty(
          "errors",
          List.of(
              new ApiExceptionHandler.FieldError("currentPassword", "Password attuale errata")));
      throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
    user.changePassword(passwordEncoder.encode(body.newPassword()));
    users.save(user);
    userSessions.closeAll(user.getUsername()); // includes the current session
  }
}
