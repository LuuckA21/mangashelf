package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AccountEmailController {
    private final AccountEmailService service;
    private final AccountEmailRequests requests;
    private final AccountMailer mailer;

    public AccountEmailController(AccountEmailService service, AccountEmailRequests requests, AccountMailer mailer) {
        this.service = service;
        this.requests = requests;
        this.mailer = mailer;
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record TokenRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                               @NotBlank @Size(min = 10, max = 200) String newPassword) {}

    @GetMapping("/email-options")
    public Map<String, Boolean> options() {
        return Map.of("enabled", mailer.enabled());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        return request(body, request, false);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resend(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        return request(body, request, true);
    }

    private ResponseEntity<Void> request(EmailRequest body, HttpServletRequest request, boolean verification) {
        requests.limit(request.getRemoteAddr());
        if (mailer.enabled()) {
            requests.submit(body.email().trim().toLowerCase(Locale.ROOT),
                    verification);
        }
        // Identical status/body for absent, disabled, unverified and throttled recipients.
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verify(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        requests.limit(request.getRemoteAddr());
        service.verify(body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        requests.limit(request.getRemoteAddr());
        service.reset(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }
}
