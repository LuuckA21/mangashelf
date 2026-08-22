package me.luucka.mangashelf.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal carrying the database id alongside the username.
 *
 * <p>Spring's built-in {@code User} only holds the username, which would
 * force an extra lookup on every request that needs to scope a query to the
 * current user — which, in this application, is almost all of them.
 *
 * @param id       primary key of the account
 * @param username login name
 * @param password BCrypt hash, erased by Spring after authentication
 * @param role     granted role
 * @param enabled  whether the account may log in
 * @param sessionVersion version of the account state at authentication time
 */
public record UserPrincipal(Long id, String username, String password,
                            Role role, boolean enabled,
                            int sessionVersion) implements UserDetails {

    public static UserPrincipal from(AppUser user) {
        return new UserPrincipal(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), user.isEnabled(),
                user.getSessionVersion());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
