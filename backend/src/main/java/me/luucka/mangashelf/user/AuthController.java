package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.dto.LoginRequest;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import me.luucka.mangashelf.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AppUserRepository users;
    private final LoginAttempts attempts;

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository,
                          AppUserRepository users,
                          LoginAttempts attempts) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.users = users;
        this.attempts = attempts;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {

        if (attempts.isBlocked(request.login())) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "too_many_attempts");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.login(), request.password()));
        } catch (RuntimeException e) {
            attempts.recordFailure(request.login());
            throw e;
        }
        attempts.recordSuccess(request.login());

        // Rotating the session id is what prevents session fixation: an id
        // an attacker planted before login stops being valid the moment the
        // session becomes authenticated. But changeSessionId() throws when
        // there is no session to rotate, which is the normal case for a
        // browser arriving with no cookie at all — there the fresh session
        // created below is already unguessable, so nothing needs rotating.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        } else {
            httpRequest.getSession(true);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Explicit save: as of Spring Security 6 nothing persists the context
        // for us outside the authentication filters, so skipping this line
        // yields a login that appears to succeed and a session that does not.
        contextRepository.saveContext(context, httpRequest, httpResponse);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return users.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("user_not_found"));
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
}
