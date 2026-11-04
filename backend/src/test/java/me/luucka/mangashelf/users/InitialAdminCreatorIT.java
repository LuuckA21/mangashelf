package me.luucka.mangashelf.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.luucka.mangashelf.TestcontainersConfig;
import me.luucka.mangashelf.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    properties = {
      "app.initial-admin.username=admin",
      "app.initial-admin.password=initial-pass-123"
    })
@Import(TestcontainersConfig.class)
class InitialAdminCreatorIT {

  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired AppProperties props;

  @Test
  void createsAdminWhenNoUsers() {
    var admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.isMustChangePassword()).isTrue();
  }

  @Test
  void doesNothingWhenUsersExist() throws Exception {
    long before = users.count();
    new InitialAdminCreator(
            users,
            encoder,
            new AppProperties(null, new AppProperties.InitialAdmin("other", "initial-pass-123")))
        .run(null);
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void doesNothingWhenNotConfigured() throws Exception {
    users.deleteAll();
    new InitialAdminCreator(
            users, encoder, new AppProperties(null, new AppProperties.InitialAdmin("", "")))
        .run(null);
    assertThat(users.count()).isZero();
    // restore the shared fixture for the other tests
    new InitialAdminCreator(users, encoder, props).run(null);
  }

  @Test
  void failsOnStartupWhenPasswordTooShort() {
    var creator =
        new InitialAdminCreator(
            users, encoder, new AppProperties(null, new AppProperties.InitialAdmin("x", "short")));
    users.deleteAll();
    try {
      assertThatThrownBy(() -> creator.run(null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("12");
    } finally {
      new InitialAdminCreator(users, encoder, props).run(null);
    }
  }
}
