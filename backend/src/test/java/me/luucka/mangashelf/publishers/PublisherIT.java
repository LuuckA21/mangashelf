package me.luucka.mangashelf.publishers;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PublisherIT {

  private static final String PASSWORD = "correct-horse-1";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie admin;
  Cookie user;

  @BeforeEach
  void seed() throws Exception {
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    jdbc.update("DELETE FROM publishers");
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    users.save(new AppUser("Anna", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    admin = session("Admin");
    user = session("Anna");
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

  private static String name(String n) {
    return "{\"name\":\"%s\"}".formatted(n);
  }

  private static String read(ResultActions r, String path) throws Exception {
    return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
  }

  private String publisher(String n) throws Exception {
    return read(
        send(post("/api/v1/publishers"), admin, name(n)).andExpect(status().isCreated()), "$.id");
  }

  private String series(String title) throws Exception {
    return read(send(post("/api/v1/series"), user, "{\"title\":\"%s\"}".formatted(title)), "$.id");
  }

  private ResultActions createEdition(String seriesId, String editionName, String publisherId)
      throws Exception {
    var pub = publisherId == null ? "null" : "\"%s\"".formatted(publisherId);
    return send(
        post("/api/v1/series/" + seriesId + "/editions"),
        user,
        "{\"name\":\"%s\",\"publisherId\":%s,\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":5}"
            .formatted(editionName, pub));
  }

  private void own(String editionId, int... numbers) throws Exception {
    send(put("/api/v1/collection/editions/" + editionId), user, null).andExpect(status().isOk());
    var list = new StringBuilder();
    for (var n : numbers) {
      list.append(list.isEmpty() ? "" : ",").append(n);
    }
    send(
            put("/api/v1/collection/editions/" + editionId + "/volumes"),
            user,
            "{\"owned\":[%s]}".formatted(list))
        .andExpect(status().isOk());
  }

  @Test
  void adminAddsRenamesDeletes() throws Exception {
    var created = send(post("/api/v1/publishers"), admin, name("  Star Comics "));
    created
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Star Comics"))
        .andExpect(
            header()
                .string("Location", org.hamcrest.Matchers.containsString("/api/v1/publishers/")));
    var id = read(created, "$.id");
    send(patch("/api/v1/publishers/" + id), admin, name("Panini"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Panini"));
    send(delete("/api/v1/publishers/" + id), admin, null).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/publishers").cookie(admin)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listIsSortedCaseInsensitiveForEveryUser() throws Exception {
    publisher("beta");
    publisher("Alfa");
    publisher("Gamma");
    mvc.perform(get("/api/v1/publishers").cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(contains("Alfa", "beta", "Gamma")));
  }

  @Test
  void nonAdminWritesAre403() throws Exception {
    var id = publisher("Panini");
    send(post("/api/v1/publishers"), user, name("X")).andExpect(status().isForbidden());
    send(patch("/api/v1/publishers/" + id), user, name("X")).andExpect(status().isForbidden());
    send(delete("/api/v1/publishers/" + id), user, null).andExpect(status().isForbidden());
  }

  @Test
  void duplicateNameRejectedIgnoringCaseAndSpaces() throws Exception {
    publisher("Star Comics");
    send(post("/api/v1/publishers"), admin, name(" star comics "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].message").value("Editore già presente"));
    var other = publisher("Panini");
    send(patch("/api/v1/publishers/" + other), admin, name("STAR COMICS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void renameToSameNameDifferentCaseIsAllowed() throws Exception {
    var id = publisher("star comics");
    send(patch("/api/v1/publishers/" + id), admin, name("Star Comics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Star Comics"));
  }

  @Test
  void blankOrTooLongNameIs400() throws Exception {
    send(post("/api/v1/publishers"), admin, name("   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    send(post("/api/v1/publishers"), admin, name("x".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void deleteInUseIs409() throws Exception {
    var p = publisher("Panini");
    createEdition(series("Berserk"), "Deluxe", p).andExpect(status().isCreated());
    send(delete("/api/v1/publishers/" + p), admin, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("L'editore è usato da alcune edizioni"));
  }

  @Test
  void editionWithPublisherWithoutAndUnknown() throws Exception {
    var p = publisher("Panini");
    var s = series("Berserk");
    createEdition(s, "A", p)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publisherId").value(p))
        .andExpect(jsonPath("$.publisher").value("Panini"));
    createEdition(s, "B", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publisherId").value(nullValue()))
        .andExpect(jsonPath("$.publisher").value(nullValue()));
    createEdition(s, "C", UUID.randomUUID().toString())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("publisherId"))
        .andExpect(jsonPath("$.errors[0].message").value("Editore non trovato"));
  }

  @Test
  void renameShowsInEditionAndCollection() throws Exception {
    var p = publisher("Panini");
    var e = read(createEdition(series("Berserk"), "A", p), "$.id");
    own(e, 1);
    send(patch("/api/v1/publishers/" + p), admin, name("Planet")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/editions/" + e).cookie(user))
        .andExpect(jsonPath("$.publisher").value("Planet"));
    mvc.perform(get("/api/v1/collection/editions/" + e).cookie(user))
        .andExpect(jsonPath("$.publisher").value("Planet"));
    mvc.perform(get("/api/v1/collection").cookie(user))
        .andExpect(jsonPath("$.content[0].publisher").value("Planet"));
    mvc.perform(get("/api/v1/collection/stats").cookie(user))
        .andExpect(jsonPath("$.byPublisher[0].key").value("Planet"));
  }

  @Test
  void statsAndCollectionUsePublisherTable() throws Exception {
    var p = publisher("Panini");
    own(read(createEdition(series("S1"), "A", p), "$.id"), 1, 2, 3);
    own(read(createEdition(series("S2"), "B", null), "$.id"), 1, 2);
    mvc.perform(get("/api/v1/collection/stats").cookie(user))
        .andExpect(jsonPath("$.byPublisher.length()").value(2))
        .andExpect(jsonPath("$.byPublisher[0].key").value("Panini"))
        .andExpect(jsonPath("$.byPublisher[0].volumes").value(3))
        .andExpect(jsonPath("$.byPublisher[1].key").value(nullValue()))
        .andExpect(jsonPath("$.byPublisher[1].volumes").value(2));
  }

  @Test
  void oldTextColumnIsIgnored() throws Exception {
    var e = read(createEdition(series("Berserk"), "A", null), "$.id");
    jdbc.update("UPDATE editions SET publisher = 'Legacy' WHERE id = ?::uuid", e);
    mvc.perform(get("/api/v1/editions/" + e).cookie(user))
        .andExpect(jsonPath("$.publisher").value(nullValue()));
    // a legacy text value sent by an old client is ignored, never stored in the old column
    send(
            post("/api/v1/series/" + series("Naruto") + "/editions"),
            user,
            "{\"name\":\"B\",\"publisher\":\"Old\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":5}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publisher").value(nullValue()));
    org.junit.jupiter.api.Assertions.assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM editions WHERE publisher = 'Old'", Integer.class));
  }

  @Test
  void migrationLeavesExistingEditionsWithoutPublisher() throws Exception {
    var e = read(createEdition(series("Berserk"), "A", null), "$.id");
    // simulates an edition that predates the migration: only the legacy column is set
    jdbc.update(
        "UPDATE editions SET publisher = 'Panini', publisher_id = NULL WHERE id = ?::uuid", e);
    mvc.perform(get("/api/v1/editions/" + e).cookie(user))
        .andExpect(jsonPath("$.publisherId").value(nullValue()))
        .andExpect(jsonPath("$.publisher").value(nullValue()));
  }
}
