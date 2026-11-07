package me.luucka.mangashelf.users;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class AccountDeletionIT {

  private static final String PASSWORD = "correct-horse-1";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    // users first: their lists and rows cascade, and rows restrict deleting editions
    users.deleteAll();
    // the catalog is shared: another IT may have left a series with the same title
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    jdbc.update("DELETE FROM spring_session");
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    users.save(new AppUser("Mario", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
  }

  private Cookie session(String username) throws Exception {
    return cookie(login(mvc, username, PASSWORD).andReturn().getResponse(), "SESSION")
        .orElseThrow();
  }

  private ResultActions deleteAccount(Cookie session, String password) throws Exception {
    var token = xsrf(mvc);
    return mvc.perform(
        delete("/api/v1/account")
            .cookie(session, token)
            .header("X-XSRF-TOKEN", token.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"%s\"}".formatted(password)));
  }

  @Test
  void deleteAccountWithWrongPasswordIs400() throws Exception {
    deleteAccount(session("Mario"), "not-the-password-1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
    assertThat(users.findByUsernameIgnoreCase("Mario")).isPresent();
  }

  @Test
  void deleteAccountRemovesUserAndLogsOut() throws Exception {
    var session = session("Mario");
    deleteAccount(session, PASSWORD).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    assertThat(users.findByUsernameIgnoreCase("Mario")).isEmpty();
  }

  @Test
  void lastAdminCannotDeleteAccountIs409() throws Exception {
    deleteAccount(session("Admin"), PASSWORD).andExpect(status().isConflict());
    assertThat(users.findByUsernameIgnoreCase("Admin")).isPresent();
  }

  private String postJson(Cookie session, String url, String body) throws Exception {
    var token = xsrf(mvc);
    return mvc.perform(
            post(url)
                .cookie(session, token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void deletingAccountDeletesPurchases() throws Exception {
    var session = session("Mario");
    String series =
        JsonPath.read(postJson(session, "/api/v1/series", "{\"title\":\"Berserk\"}"), "$.id");
    String edition =
        JsonPath.read(
            postJson(
                session,
                "/api/v1/series/" + series + "/editions",
                "{\"name\":\"Blu\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":10}"),
            "$.id");
    String list =
        JsonPath.read(
            postJson(
                session,
                "/api/v1/purchase-lists",
                "{\"description\":\"Novembre\",\"month\":11,\"year\":2026}"),
            "$.id");
    postJson(
        session,
        "/api/v1/purchase-lists/" + list + "/items",
        "{\"editionId\":\"%s\",\"volume\":3,\"priceEurCents\":1000,\"priceChfCents\":1100}"
            .formatted(edition));
    assertThat(jdbc.queryForObject("select count(*) from purchase_items", Integer.class))
        .isEqualTo(1);

    deleteAccount(session, PASSWORD).andExpect(status().isNoContent());

    assertThat(jdbc.queryForObject("select count(*) from purchase_lists", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from purchase_items", Integer.class)).isZero();
  }
}
