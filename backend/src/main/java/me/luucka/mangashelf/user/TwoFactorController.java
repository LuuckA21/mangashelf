package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {
    private final TwoFactorService factors;
    private final LoginSession sessions;

    public TwoFactorController(TwoFactorService factors, LoginSession sessions) {
        this.factors = factors;
        this.sessions = sessions;
    }

    public record Code(@NotBlank @Size(max = 64) String code) {
        @Override public String toString() { return "Code[redacted]"; }
    }
    public record Credentials(@NotBlank @Size(max = 200) String currentPassword,
                              @Size(max = 64) String code) {
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    public record Recovery(List<String> recoveryCodes, UserResponse user) {
        @Override public String toString() { return "Recovery[redacted]"; }
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody Code body, HttpServletRequest request,
                              HttpServletResponse response) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(LoginSession.PENDING) instanceof LoginSession.Pending pending)) {
            throw challengeExpired();
        }
        synchronized (pending) {
            if (pending.consumed || pending.attempts >= 5 || !pending.expires.isAfter(Instant.now())) {
                throw challengeExpired();
            }
            pending.attempts++;
            // Reserve outside the transaction; a rollback cannot forgive a failed guess.
            try (var permit = factors.reserve(pending.principal.id(), request.getRemoteAddr())) {
                AppUser user = factors.verifyLogin(pending.principal, body.code());
                pending.consumed = true;
                permit.succeeded();
                return sessions.authenticate(user, request, response);
            }
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(HttpServletRequest request) {
        LoginSession.clear(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public TwoFactorService.Status status(@AuthenticationPrincipal UserPrincipal principal) {
        return factors.status(principal);
    }

    @PostMapping("/setup")
    public TwoFactorService.Setup setup(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody Credentials body, HttpServletRequest request) {
        try (var permit = factors.reserve(principal.id(), request.getRemoteAddr())) {
            var result = factors.setup(principal, body.currentPassword());
            permit.succeeded();
            return result;
        }
    }

    @DeleteMapping("/setup")
    public ResponseEntity<Void> cancelSetup(@AuthenticationPrincipal UserPrincipal principal) {
        factors.cancelSetup(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/enable")
    public Recovery enable(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody Code body,
                            HttpServletRequest request, HttpServletResponse response) {
        try (var permit = factors.reserve(principal.id(), request.getRemoteAddr())) {
            var result = factors.enable(principal, body.code());
            permit.succeeded();
            return new Recovery(result.codes(), sessions.authenticate(result.account(), request, response));
        }
    }

    @PostMapping("/disable")
    public UserResponse disable(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody Credentials body,
                                HttpServletRequest request, HttpServletResponse response) {
        try (var permit = factors.reserve(principal.id(), request.getRemoteAddr())) {
            var result = factors.disable(principal, body.currentPassword(), body.code());
            permit.succeeded();
            return sessions.authenticate(result, request, response);
        }
    }

    @PostMapping("/recovery-codes")
    public Recovery regenerate(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody Credentials body,
                               HttpServletRequest request, HttpServletResponse response) {
        try (var permit = factors.reserve(principal.id(), request.getRemoteAddr())) {
            var result = factors.regenerate(principal, body.currentPassword(), body.code());
            permit.succeeded();
            return new Recovery(result.codes(), sessions.authenticate(result.account(), request, response));
        }
    }

    private static ApiException challengeExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "two_factor_challenge_expired");
    }
}
