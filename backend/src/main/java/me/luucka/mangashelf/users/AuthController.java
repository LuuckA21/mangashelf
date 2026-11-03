package me.luucka.mangashelf.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import me.luucka.mangashelf.users.dto.LoginRequest;
import me.luucka.mangashelf.users.dto.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
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

  public AuthController(
      AuthenticationManager authenticationManager, CsrfTokenRepository csrfTokenRepository) {
    this.authenticationManager = authenticationManager;
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
}
