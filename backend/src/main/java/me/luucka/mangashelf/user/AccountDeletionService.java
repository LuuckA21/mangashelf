package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Service
public class AccountDeletionService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AccountMailer mailer;
    private final AccountAdministration administration;
    private final AdminAuditEventRepository audit;
    private final SecureRandom random = new SecureRandom();

    public AccountDeletionService(AppUserRepository users, PasswordEncoder encoder, AccountMailer mailer,
                                  AccountAdministration administration, AdminAuditEventRepository audit) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.administration = administration;
        this.audit = audit;
    }

    @Transactional
    public void request(UserPrincipal principal, String password) {
        if (!mailer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_unavailable");
        administration.lock();
        AppUser user = users.findByIdForUpdate(principal.id()).orElseThrow(AccountDeletionService::invalidToken);
        if (!user.isEnabled() || !user.isEmailVerified() || user.getSessionVersion() != principal.sessionVersion()) {
            throw ApiException.forbidden("session_invalid");
        }
        if (AuthService.passwordTooLong(password) || !encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("current_password_invalid");
        }
        administration.requireAnotherAdministrator(user);
        Instant now = Instant.now();
        if (user.getDeletionRequestedAt() != null && user.getDeletionRequestedAt().plusSeconds(60).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "deletion_cooldown");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setDeletionHash(AccountEmailService.hash(token));
        user.setDeletionExpiresAt(now.plus(Duration.ofMinutes(30)));
        user.setDeletionSessionVersion(user.getSessionVersion());
        user.setDeletionRequestedAt(now);
        users.flush();
        try {
            mailer.sendDeletion(user, token);
        } catch (MailException ex) {
            // Roll back token/cooldown changes; an older delivered link remains valid.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_unavailable");
        }
    }

    public record Details(String username, String email) {}

    @Transactional(readOnly = true)
    public Details details(String token, UserPrincipal principal) {
        String hash = AccountEmailService.hash(token);
        Long id = users.findIdByDeletionHash(hash).orElseThrow(AccountDeletionService::invalidToken);
        AppUser user = users.findById(id).orElseThrow(AccountDeletionService::invalidToken);
        validate(user, hash, principal);
        return new Details(user.getUsername(), user.getEmail());
    }

    @Transactional
    public void confirm(String token, UserPrincipal principal) {
        administration.lock();
        String hash = AccountEmailService.hash(token);
        Long id = users.findIdByDeletionHash(hash).orElseThrow(AccountDeletionService::invalidToken);
        AppUser user = users.findByIdForUpdate(id).orElseThrow(AccountDeletionService::invalidToken);
        validate(user, hash, principal);
        administration.requireAnotherAdministrator(user);
        audit.anonymizeActor(id);
        audit.anonymizeTarget(id);
        // Database cascades remove only this user's collection and purchase lists/items.
        // A missing account invalidates every stored session through AccountStateFilter.
        users.delete(user);
        users.flush();
    }

    private static void validate(AppUser user, String hash, UserPrincipal principal) {
        if (!user.isEnabled() || !user.isEmailVerified() || !hash.equals(user.getDeletionHash())
                || user.getDeletionExpiresAt() == null || !user.getDeletionExpiresAt().isAfter(Instant.now())
                || !Objects.equals(user.getDeletionSessionVersion(), user.getSessionVersion())) {
            throw invalidToken();
        }
        if (principal != null && !principal.id().equals(user.getId())) {
            throw ApiException.forbidden("deletion_wrong_account");
        }
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("deletion_token_invalid");
    }
}
