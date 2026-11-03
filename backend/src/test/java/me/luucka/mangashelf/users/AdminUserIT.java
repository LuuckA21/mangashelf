package me.luucka.mangashelf.users;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    admin = users.save(user("Admin", Role.ADMIN));
    other = users.save(user("Mario", Role.USER));
  }

  private AppUser user(String name, Role role) {
    return new AppUser(name, encoder.encode(PASSWORD), role, false, Instant.now());
  }

  private Cookie session(String username) throws Exception {
    return cookie(login(mvc, username, PASSWORD).andReturn().getResponse(), "SESSION")
        .orElseThrow();
  }

  private ResultActions send(MockHttpServletRequestBuilder req, Cookie session, String body)
      throws Exception {
    var token = xsrf(mvc);
    req.cookie(session, token).header("X-XSRF-TOKEN", token.getValue());
    if (body != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(req);
  }

  private static String create(String username, String password, String role) {
    return "{\"username\":\"%s\",\"temporaryPassword\":\"%s\",\"role\":\"%s\"}"
        .formatted(username, password, role);
  }

  @Test
  void nonAdminGets403() throws Exception {
    mvc.perform(get("/api/v1/users").cookie(session("Mario"))).andExpect(status().isForbidden());
  }

  @Test
  void listIsPagedAndSortedByUsername() throws Exception {
    mvc.perform(get("/api/v1/users").cookie(session("Admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].username").value("Admin"))
        .andExpect(jsonPath("$.content[1].username").value("Mario"))
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void pageSizeIsCappedAt100() throws Exception {
    mvc.perform(get("/api/v1/users").param("size", "101").cookie(session("Admin")))
        .andExpect(jsonPath("$.page.size").value(100));
  }

  @Test
  void createUserReturns201WithLocationAndForcesPasswordChange() throws Exception {
    send(post("/api/v1/users"), session("Admin"), create("luca", "temporary-pass-1", "USER"))
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
    var admin = session("Admin");
    send(post("/api/v1/users"), admin, create("Luca", "temporary-pass-1", "USER"))
        .andExpect(status().isCreated());
    send(post("/api/v1/users"), admin, create("LUCA", "temporary-pass-1", "USER"))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidUsernameIs400() throws Exception {
    var admin = session("Admin");
    for (var bad : new String[] {"a b", "ab"}) {
      send(post("/api/v1/users"), admin, create(bad, "temporary-pass-1", "USER"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("username"));
    }
  }

  @Test
  void weakTemporaryPasswordIs400() throws Exception {
    send(post("/api/v1/users"), session("Admin"), create("luca", "short", "USER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("temporaryPassword"));
  }

  @Test
  void resetPasswordSetsFlagAndClosesSessions() throws Exception {
    var victim = session("Mario");
    send(
            post("/api/v1/users/" + other.getId() + "/reset-password"),
            session("Admin"),
            "{\"temporaryPassword\":\"another-temp-pass-2\"}")
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
    login(mvc, "Mario", "another-temp-pass-2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void changeRoleClosesSessions() throws Exception {
    var victim = session("Mario");
    send(patch("/api/v1/users/" + other.getId()), session("Admin"), "{\"role\":\"ADMIN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
  }

  @Test
  void cannotChangeOwnRoleIs409() throws Exception {
    send(patch("/api/v1/users/" + admin.getId()), session("Admin"), "{\"role\":\"USER\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void cannotDemoteLastAdminIs409() throws Exception {
    var stale = session("Admin");
    var second = users.save(user("Second", Role.ADMIN));
    // The caller's session outlives a role change made behind the API: Second is the only admin.
    jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());
    send(patch("/api/v1/users/" + second.getId()), stale, "{\"role\":\"USER\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void nonAdminCannotPatch() throws Exception {
    send(patch("/api/v1/users/" + admin.getId()), session("Mario"), "{\"role\":\"USER\"}")
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownUserIs404() throws Exception {
    send(delete("/api/v1/users/" + java.util.UUID.randomUUID()), session("Admin"), null)
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteUserCascadesAndClosesSessions() throws Exception {
    var victim = session("Mario");
    send(delete("/api/v1/users/" + other.getId()), session("Admin"), null)
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(victim)).andExpect(status().isUnauthorized());
    org.assertj.core.api.Assertions.assertThat(users.existsById(other.getId())).isFalse();
  }

  @Test
  void cannotDeleteSelfIs409() throws Exception {
    send(delete("/api/v1/users/" + admin.getId()), session("Admin"), null)
        .andExpect(status().isConflict());
  }

  @Test
  void cannotDeleteLastAdminIs409() throws Exception {
    var stale = session("Admin");
    var second = users.save(user("Second", Role.ADMIN));
    jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());
    send(delete("/api/v1/users/" + second.getId()), stale, null).andExpect(status().isConflict());
  }
}
