package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.dto.LoginRequest;
import me.luucka.mangashelf.user.dto.LanguageRequest;
import me.luucka.mangashelf.user.dto.PasswordChangeRequest;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import me.luucka.mangashelf.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final LoginSession loginSession;
    private final TwoFactorService twoFactor;
    private final AppUserRepository users;
    private final LoginAttempts attempts;
    private final RegistrationAttempts registrationAttempts;

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          LoginSession loginSession, TwoFactorService twoFactor,
                          AppUserRepository users,
                          LoginAttempts attempts,
                          RegistrationAttempts registrationAttempts) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.loginSession = loginSession;
        this.twoFactor = twoFactor;
        this.users = users;
        this.attempts = attempts;
        this.registrationAttempts = registrationAttempts;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        if (!registrationAttempts.tryAcquire(httpRequest.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "too_many_registrations");
        }
        AppUser user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {

        String accountKey = loginAttemptKey(request.login());
        String clientAddress = httpRequest.getRemoteAddr();
        LoginAttempts.Attempt attempt = attempts.tryAcquire(accountKey, clientAddress);
        if (attempt == null) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "too_many_attempts");
        }

        Authentication authentication;
        try (attempt) {
            if (AuthService.passwordTooLong(request.password())) {
                throw new BadCredentialsException("Password exceeds BCrypt limit");
            }
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.login(), request.password()));
            attempt.succeeded();
        }

        AppUser current = twoFactor.current((UserPrincipal) authentication.getPrincipal());
        if (current.getTwoFactorSecret() != null) {
            loginSession.challenge(current, httpRequest);
            return java.util.Map.of("twoFactorRequired", true);
        }
        return loginSession.authenticate(current, httpRequest, httpResponse);
    }

    /** Stable key shared by the username and email aliases of one account. */
    private String loginAttemptKey(String login) {
        return users.findByUsernameIgnoreCase(login)
                .or(() -> users.findByEmailIgnoreCase(login))
                .map(user -> "user:" + user.getId())
                .orElseGet(() -> "login:" + login.trim().toLowerCase(Locale.ROOT));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** Lets the frontend restore its session on page load. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return users.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("user_not_found"));
    }

    /** Persists the interface language for the signed-in account. */
    @PutMapping("/me/language")
    public UserResponse updateLanguage(@Valid @RequestBody LanguageRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return UserResponse.from(
                authService.updateLanguage(principal.id(), request.language()));
    }

    /** Changes credentials and signs every active device out. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        try (var permit = twoFactor.reserve(principal.id(), httpRequest.getRemoteAddr())) {
            authService.updatePassword(
                    principal.id(), request.currentPassword(), request.newPassword(), request.code());
            permit.succeeded();
        }

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
