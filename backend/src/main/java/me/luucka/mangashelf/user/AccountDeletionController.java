package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AccountDeletionController {
    private final AccountDeletionService service;
    private final AccountEmailRequests limits;

    public AccountDeletionController(AccountDeletionService service, AccountEmailRequests limits) {
        this.service = service;
        this.limits = limits;
    }

    public record Request(@NotBlank @Size(max = 200) String currentPassword) {}
    public record Confirmation(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                               @NotNull @AssertTrue Boolean confirmed) {}

    @PostMapping("/me/deletion-request")
    public ResponseEntity<Void> request(@Valid @RequestBody Request body,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        limits.limit(request.getRemoteAddr());
        service.request(principal, body.currentPassword());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/deletion-details")
    public AccountDeletionService.Details details(@Valid @RequestBody AccountEmailController.TokenRequest body,
                                                 @AuthenticationPrincipal UserPrincipal principal,
                                                 HttpServletRequest request) {
        limits.limit(request.getRemoteAddr());
        return service.details(body.token(), principal);
    }

    @PostMapping("/delete-account")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Confirmation body,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        limits.limit(request.getRemoteAddr());
        service.confirm(body.token(), principal);
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
