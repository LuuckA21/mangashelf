package me.luucka.mangashelf.users;

import java.time.Instant;
import me.luucka.mangashelf.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialAdminCreator implements ApplicationRunner {

  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final AppProperties props;

  public InitialAdminCreator(
      AppUserRepository users, PasswordEncoder encoder, AppProperties props) {
    this.users = users;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    var admin = props.initialAdmin();
    if (admin == null
        || !StringUtils.hasText(admin.username())
        || !StringUtils.hasText(admin.password())
        || users.count() > 0) {
      return;
    }
    if (admin.password().length() < 12) {
      throw new IllegalStateException(
          "ADMIN_INITIAL_PASSWORD deve avere almeno 12 caratteri (initial admin password too short)");
    }
    users.save(
        new AppUser(
            admin.username(), encoder.encode(admin.password()), Role.ADMIN, true, Instant.now()));
  }
}
