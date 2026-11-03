package me.luucka.mangashelf.users;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import me.luucka.mangashelf.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PasswordChangeIT {

  private static final String OLD = "temporary-pass-1";
  private static final String NEW = "brand-new-password-2";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(new AppUser("Mario", encoder.encode(OLD), Role.USER, true, Instant.now()));
  }

  private Cookie session() throws Exception {
    return cookie(login(mvc, "Mario", OLD).andReturn().getResponse(), "SESSION").orElseThrow();
  }

  private ResultActions changePassword(Cookie session, String current, String next)
      throws Exception {
    var token = xsrf(mvc);
    return mvc.perform(
        post("/api/v1/auth/password")
            .cookie(session, token)
            .header("X-XSRF-TOKEN", token.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
  }

  @Test
  void temporaryPasswordBlocksOtherApis() throws Exception {
    var session = session();
    mvc.perform(get("/api/v1/series").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void changePasswordClearsFlagAndClosesAllSessions() throws Exception {
    var first = session();
    var second = session();
    changePassword(first, OLD, NEW).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isUnauthorized());
    login(mvc, "Mario", NEW)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test
  void wrongCurrentPasswordIs400() throws Exception {
    changePassword(session(), "not-the-password-1", NEW)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
  }

  @Test
  void tooShortNewPasswordIs400() throws Exception {
    changePassword(session(), OLD, "a".repeat(11))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
  }

  @Test
  void tooLongNewPasswordIs400() throws Exception {
    changePassword(session(), OLD, "a".repeat(65))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
  }
}
