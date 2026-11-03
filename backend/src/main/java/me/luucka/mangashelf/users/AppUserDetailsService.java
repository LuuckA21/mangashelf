package me.luucka.mangashelf.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

  private final AppUserRepository users;

  public AppUserDetailsService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    return users
        .findByUsernameIgnoreCase(username)
        .map(AppUserPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
  }
}
