package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.config.AppProperties;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties properties;

    public AuthService(AppUserRepository users, PasswordEncoder encoder,
                       AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Creates an account, or fails if registration is closed or the name or
     * email is taken.
     *
     * <p>The uniqueness checks race against concurrent signups, so the unique
     * constraints in the schema remain the real guarantee; these checks only
     * exist to turn the common case into a readable message instead of a
     * constraint violation.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        if (!properties.registrationEnabled()) {
            throw ApiException.forbidden("registration_closed");
        }
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("username_taken");
        }
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("email_taken");
        }

        AppUser user = new AppUser(
                request.username(),
                request.email().toLowerCase(),
                encoder.encode(request.password()));

        // The first account to exist becomes the administrator, so a fresh
        // instance does not need a seeded password in the compose file.
        if (users.count() == 0) {
            user.setRole(Role.ADMIN);
        }

        return users.save(user);
    }
}
