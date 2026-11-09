package me.luucka.mangashelf.purchases;

import static me.luucka.mangashelf.users.AuthTestSupport.session;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import me.luucka.mangashelf.TestDatabase;
import me.luucka.mangashelf.TestcontainersConfig;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.AppUserRepository;
import me.luucka.mangashelf.users.Role;
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
class PurchaseSettingsIT {

  private static final String PASSWORD = "correct-horse-1";
  private static final String URL = "/api/v1/account/purchase-settings";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie a;
  Cookie b;

  @BeforeEach
  void seed() throws Exception {
    TestDatabase.clean(jdbc);
    users.save(new AppUser("Anna", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Bruno", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    a = session(mvc, "Anna", PASSWORD);
    b = session(mvc, "Bruno", PASSWORD);
  }

  private ResultActions save(Cookie who, String body) throws Exception {
    var token = xsrf(mvc);
    return mvc.perform(
        put(URL)
            .cookie(who, token)
            .header("X-XSRF-TOKEN", token.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void defaultsAreRateOneTwentyAndTenPercent() throws Exception {
    mvc.perform(get(URL).cookie(a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chfRate").value(1.2))
        .andExpect(jsonPath("$.discountPercent").value(10));
  }

  @Test
  void updateIsPersistedPerUser() throws Exception {
    save(a, "{\"chfRate\":1.25,\"discountPercent\":15}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chfRate").value(1.25));
    mvc.perform(get(URL).cookie(a))
        .andExpect(jsonPath("$.chfRate").value(1.25))
        .andExpect(jsonPath("$.discountPercent").value(15));
    mvc.perform(get(URL).cookie(b))
        .andExpect(jsonPath("$.chfRate").value(1.2))
        .andExpect(jsonPath("$.discountPercent").value(10));
  }

  @Test
  void invalidValuesAre400() throws Exception {
    save(a, "{\"chfRate\":0,\"discountPercent\":10}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("chfRate"));
    save(a, "{\"chfRate\":1.2,\"discountPercent\":101}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("discountPercent"));
  }

  @Test
  void requiresLogin() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }
}
