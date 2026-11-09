package me.luucka.mangashelf.users;

import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.send;
import static me.luucka.mangashelf.users.AuthTestSupport.session;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import me.luucka.mangashelf.TestDatabase;
import me.luucka.mangashelf.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AdminUserIT {

  private static final String PASSWORD = "correct-horse-1";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  AppUser admin;
  AppUser other;

  @BeforeEach
  void seed() {
    TestDatabase.clean(jdbc);
    admin = users.save(user("Admin", Role.ADMIN));
    other = users.save(user("Mario", Role.USER));
  }

  private AppUser user(String name, Role role) {
    return new AppUser(name, encoder.encode(PASSWORD), role, false, Instant.now());
  }

  private static String create(String username, String password, String role) {
    return "{\"username\":\"%s\",\"temporaryPassword\":\"%s\",\"role\":\"%s\"}"
        .formatted(username, password, role);
  }

  @Test
  void nonAdminGets403() throws Exception {
    mvc.perform(get("/api/v1/users").cookie(session(mvc, "Mario", PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listIsPagedAndSortedByUsername() throws Exception {
    mvc.perform(get("/api/v1/users").cookie(session(mvc, "Admin", PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].username").value("Admin"))
        .andExpect(jsonPath("$.content[1].username").value("Mario"))
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void pageSizeIsCappedAt100() throws Exception {
    mvc.perform(get("/api/v1/users").param("size", "101").cookie(session(mvc, "Admin", PASSWORD)))
        .andExpect(jsonPath("$.page.size").value(100));
  }

  @Test
  void createUserReturns201WithLocationAndForcesPasswordChange() throws Exception {
    send(
            mvc,
            post("/api/v1/users"),
            session(mvc, "Admin", PASSWORD),
            create("luca", "temporary-pass-1", "USER"))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/users/")))
        .andExpect(jsonPath("$.username").value("luca"))
        .andExpect(jsonPath("$.mustChangePassword").value(true));
    login(AdminUserIT.this.mvc, "luca", "temporary-pass-1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void duplicateUsernameIgnoringCaseIs409() throws Exception {
    var admin = session(mvc, "Admin", PASSWORD);
    send(mvc, post("/api/v1/users"), admin, create("Luca", "temporary-pass-1", "USER"))
        .andExpect(status().isCreated());
    send(mvc, post("/api/v1/users"), admin, create("LUCA", "temporary-pass-1", "USER"))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidUsernameIs400() throws Exception {
    var admin = session(mvc, "Admin", PASSWORD);
    for (var bad : new String[] {"a b", "ab"}) {
      send(mvc, post("/api/v1/users"), admin, create(bad, "temporary-pass-1", "USER"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("username"));
    }
  }

  @Test
  void weakTemporaryPasswordIs400() throws Exception {
    send(
            mvc,
            post("/api/v1/users"),
            session(mvc, "Admin", PASSWORD),
            create("luca", "short", "USER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("temporaryPassword"));
  }

  @Test
  void resetPasswordSetsFlagAndClosesSessions() throws Exception {
    var victim = session(mvc, "Mario", PASSWORD);
    send(
            mvc,
            post("/api/v1/users/" + other.getId() + "/reset-password"),
            session(mvc, "Admin", PASSWORD),
            "{\"temporaryPassword\":\"another-temp-pass-2\"}")
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
    login(mvc, "Mario", "another-temp-pass-2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void changeRoleClosesSessions() throws Exception {
    var victim = session(mvc, "Mario", PASSWORD);
    send(
            mvc,
            patch("/api/v1/users/" + other.getId()),
            session(mvc, "Admin", PASSWORD),
            "{\"role\":\"ADMIN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
  }

  @Test
  void cannotChangeOwnRoleIs409() throws Exception {
    send(
            mvc,
            patch("/api/v1/users/" + admin.getId()),
            session(mvc, "Admin", PASSWORD),
            "{\"role\":\"USER\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void cannotDemoteLastAdminIs409() throws Exception {
    var stale = session(mvc, "Admin", PASSWORD);
    var second = users.save(user("Second", Role.ADMIN));
    // The caller's session outlives a role change made behind the API: Second is the only admin.
    jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());
    send(mvc, patch("/api/v1/users/" + second.getId()), stale, "{\"role\":\"USER\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void nonAdminCannotPatch() throws Exception {
    send(
            mvc,
            patch("/api/v1/users/" + admin.getId()),
            session(mvc, "Mario", PASSWORD),
            "{\"role\":\"USER\"}")
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownUserIs404() throws Exception {
    send(
            mvc,
            delete("/api/v1/users/" + java.util.UUID.randomUUID()),
            session(mvc, "Admin", PASSWORD),
            null)
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteUserCascadesAndClosesSessions() throws Exception {
    var victim = session(mvc, "Mario", PASSWORD);
    send(mvc, delete("/api/v1/users/" + other.getId()), session(mvc, "Admin", PASSWORD), null)
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
    org.assertj.core.api.Assertions.assertThat(users.existsById(other.getId())).isFalse();
  }

  @Test
  void cannotDeleteSelfIs409() throws Exception {
    send(mvc, delete("/api/v1/users/" + admin.getId()), session(mvc, "Admin", PASSWORD), null)
        .andExpect(status().isConflict());
  }

  @Test
  void cannotDeleteLastAdminIs409() throws Exception {
    var stale = session(mvc, "Admin", PASSWORD);
    var second = users.save(user("Second", Role.ADMIN));
    jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());
    send(mvc, delete("/api/v1/users/" + second.getId()), stale, null)
        .andExpect(status().isConflict());
  }
}
