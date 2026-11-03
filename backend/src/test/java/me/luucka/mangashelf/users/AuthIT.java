package me.luucka.mangashelf.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import me.luucka.mangashelf.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthIT {

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired FindByIndexNameSessionRepository<?> sessionRepository;

  @BeforeEach
  void seed() {
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(
        new AppUser("Luca", encoder.encode("correct-horse-1"), Role.USER, false, Instant.now()));
  }

  private static String creds(String u, String p) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(u, p);
  }

  /** Last non-expired cookie with this name among all Set-Cookie headers. */
  private static Optional<Cookie> cookie(MockHttpServletResponse response, String name) {
    Cookie found = null;
    for (String header : response.getHeaders("Set-Cookie")) {
      if (header.startsWith(name + "=")) {
        var value = header.substring(name.length() + 1).split(";", 2)[0];
        found = value.isEmpty() ? null : new Cookie(name, value);
      }
    }
    return Optional.ofNullable(found);
  }

  private Cookie xsrf() throws Exception {
    return cookie(mvc.perform(get("/api/v1/auth/me")).andReturn().getResponse(), "XSRF-TOKEN")
        .orElseThrow();
  }

  private ResultActions login(String u, String p) throws Exception {
    return loginWith(xsrf(), u, p);
  }

  private ResultActions loginWith(Cookie xsrf, String u, String p) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .cookie(xsrf)
            .header("X-XSRF-TOKEN", xsrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(creds(u, p)));
  }

  @Test
  void meWithoutLoginIs401AndSetsXsrfCookie() throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse();
    assertThat(cookie(response, "XSRF-TOKEN")).isPresent();
  }

  @Test
  void loginWithValidCredentialsReturnsMe() throws Exception {
    var response =
        login("Luca", "correct-horse-1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("Luca"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn()
            .getResponse();
    var session = cookie(response, "SESSION").orElseThrow();
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("Luca"));
  }

  @Test
  void sessionIsStoredInJdbcWithConfiguredCookieAndTimeout() throws Exception {
    var response = login("Luca", "correct-horse-1").andReturn().getResponse();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session", Long.class))
        .isEqualTo(14L * 24 * 3600);
    assertThat(sessionRepository.findByPrincipalName("Luca")).hasSize(1);
    assertThat(response.getHeaders("Set-Cookie"))
        .anyMatch(c ->
            c.startsWith("SESSION=")
                && c.contains("HttpOnly")
                && c.contains("SameSite=Lax")
                && c.contains("Secure"));
  }

  @Test
  void loginRotatesXsrfToken() throws Exception {
    var before = xsrf();
    var response = loginWith(before, "Luca", "correct-horse-1").andReturn().getResponse();
    assertThat(cookie(response, "XSRF-TOKEN").orElseThrow().getValue())
        .isNotEqualTo(before.getValue());
  }

  @Test
  void loginIsCaseInsensitiveOnUsername() throws Exception {
    login("luca", "correct-horse-1").andExpect(status().isOk());
  }

  @Test
  void wrongPasswordAndUnknownUserGiveSameMessage() throws Exception {
    login("Luca", "wrong-password-1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Credenziali non valide"));
    login("nobody", "correct-horse-1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Credenziali non valide"));
  }

  @Test
  void loginWithoutCsrfIs403() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(creds("Luca", "correct-horse-1")))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutEndsSessionAndClearsXsrf() throws Exception {
    var session =
        cookie(login("Luca", "correct-horse-1").andReturn().getResponse(), "SESSION").orElseThrow();
    var token = xsrf();
    var response =
        mvc.perform(
                post("/api/v1/auth/logout")
                    .cookie(session, token)
                    .header("X-XSRF-TOKEN", token.getValue()))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(cookie(response, "XSRF-TOKEN")).isEmpty();
    mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void validationErrorIsProblemDetail() throws Exception {
    login("", "correct-horse-1")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
  }
}
