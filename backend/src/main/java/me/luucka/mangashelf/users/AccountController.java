package me.luucka.mangashelf.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import me.luucka.mangashelf.users.dto.AccountExport;
import me.luucka.mangashelf.users.dto.DeleteAccountRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

  private final AccountService service;
  private final CsrfLogoutHandler csrfLogout;

  public AccountController(AccountService service, CsrfTokenRepository csrfTokenRepository) {
    this.service = service;
    this.csrfLogout = new CsrfLogoutHandler(csrfTokenRepository);
  }

  @GetMapping("/export")
  public AccountExport export(@AuthenticationPrincipal AppUserPrincipal me) {
    return service.export(me.id());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AppUserPrincipal me,
      @Valid @RequestBody DeleteAccountRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    service.delete(me.id(), body.password()); // closes the current session too
    SecurityContextHolder.clearContext();
    csrfLogout.logout(request, response, null);
  }
}
