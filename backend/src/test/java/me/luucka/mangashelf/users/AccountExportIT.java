package me.luucka.mangashelf.users;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import me.luucka.mangashelf.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AccountExportIT {

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  private String send(MockHttpServletRequestBuilder req, Cookie session, String body)
      throws Exception {
    var token = xsrf(mvc);
    req.cookie(session, token).header("X-XSRF-TOKEN", token.getValue());
    if (body != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(req)
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void exportsProfileAndCollection() throws Exception {
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(
        new AppUser("Anna", encoder.encode("correct-horse-1"), Role.USER, false, Instant.now()));
    var s =
        cookie(login(mvc, "Anna", "correct-horse-1").andReturn().getResponse(), "SESSION")
            .orElseThrow();
    String series =
        JsonPath.read(send(post("/api/v1/series"), s, "{\"title\":\"Berserk\"}"), "$.id");
    String edition =
        JsonPath.read(
            send(
                post("/api/v1/series/" + series + "/editions"),
                s,
                "{\"name\":\"Blu\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":10}"),
            "$.id");
    send(put("/api/v1/collection/editions/" + edition + "/volumes"), s, "{\"owned\":[1,4]}");

    mvc.perform(get("/api/v1/account/export").cookie(s))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("Anna"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.collection[0].seriesTitle").value("Berserk"))
        .andExpect(jsonPath("$.collection[0].owned[1]").value(4));
  }
}
