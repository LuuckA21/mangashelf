package me.luucka.mangashelf.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Accepts either the username or the email, so a user who forgot which
     * one they registered with can still get in.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        return users.findByUsernameIgnoreCase(login)
                .or(() -> users.findByEmailIgnoreCase(login))
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + login));
    }
}
