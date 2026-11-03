package me.luucka.mangashelf.users;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthIT {

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;

  @BeforeEach
  void seed() {
    users.deleteAll();
    users.save(
        new AppUser("Luca", encoder.encode("correct-horse-1"), Role.USER, false, Instant.now()));
  }

  private static String creds(String u, String p) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(u, p);
  }

  // Real SPA flow: the csrf() post-processor would patch the shared filter and make tests
  // order-dependent.
  private Cookie xsrf() throws Exception {
    var header =
        mvc.perform(get("/api/v1/auth/me")).andReturn().getResponse().getHeader("Set-Cookie");
    return new Cookie("XSRF-TOKEN", header.substring("XSRF-TOKEN=".length(), header.indexOf(';')));
  }

  private org.springframework.test.web.servlet.ResultActions login(String u, String p)
      throws Exception {
    var xsrf = xsrf();
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
    assertThat(response.getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("XSRF-TOKEN="));
  }

  @Test
  void loginWithValidCredentialsReturnsMe() throws Exception {
    var result =
        login("Luca", "correct-horse-1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("Luca"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("Luca"));
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
  void logoutEndsSession() throws Exception {
    var session =
        (MockHttpSession)
            login("Luca", "correct-horse-1").andReturn().getRequest().getSession(false);
    var token = xsrf();
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .session(session))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void validationErrorIsProblemDetail() throws Exception {
    login("", "correct-horse-1")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
  }
}
