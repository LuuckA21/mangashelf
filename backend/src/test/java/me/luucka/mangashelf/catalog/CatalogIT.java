package me.luucka.mangashelf.catalog;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CatalogIT {

  private static final String PASSWORD = "correct-horse-1";
  private static final String EDITION =
      "{\"name\":\"Edizione blu\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":114}";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie admin;
  Cookie mario;

  @BeforeEach
  void seed() throws Exception {
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    users.save(new AppUser("Mario", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    admin = session("Admin");
    mario = session("Mario");
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

  private String createSeries(String title) throws Exception {
    var body =
        send(post("/api/v1/series"), mario, "{\"title\":\"%s\"}".formatted(title))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String createEdition(String seriesId, String json) throws Exception {
    var body =
        send(post("/api/v1/series/" + seriesId + "/editions"), mario, json)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private static String editionJson(String name, long version) {
    return "{\"name\":\"%s\",\"type\":\"VARIANT\",\"status\":\"COMPLETED\",\"volumeCount\":10,\"version\":%d}"
        .formatted(name, version);
  }

  @Test
  void anyUserCreatesSeriesAndEdition() throws Exception {
    send(post("/api/v1/series"), mario, "{\"title\":\"One Piece\"}")
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.title").value("One Piece"));
    var id = jdbc.queryForObject("SELECT id::text FROM series", String.class);
    send(post("/api/v1/series/" + id + "/editions"), mario, EDITION)
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.publisher").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.seriesTitle").value("One Piece"))
        .andExpect(jsonPath("$.canEditCover").value(true))
        .andExpect(jsonPath("$.hasCover").value(false))
        .andExpect(jsonPath("$.collectionCount").value(0))
        .andExpect(jsonPath("$.inMyCollection").value(false));
  }

  @Test
  void validationMessagesAreItalianWhateverTheBrowserLocale() throws Exception {
    var req = post("/api/v1/series").header("Accept-Language", "en");
    send(req, mario, "{\"title\":\"  \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message").value("Campo obbligatorio"));
  }

  @Test
  void unauthenticatedIs401() throws Exception {
    mvc.perform(get("/api/v1/series")).andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateTitleTrimmedAndIgnoringCaseIs409() throws Exception {
    var one = createSeries("One Piece");
    send(post("/api/v1/series"), mario, "{\"title\":\" one piece \"}")
        .andExpect(status().isConflict());
    createEdition(one, EDITION);
    send(post("/api/v1/series/" + one + "/editions"), mario, EDITION.replace("blu", "BLU "))
        .andExpect(status().isConflict());
    var other = createSeries("Naruto");
    createEdition(other, EDITION);
  }

  @Test
  void blankPublisherBecomesNull() throws Exception {
    var id = createSeries("One Piece");
    send(
            post("/api/v1/series/" + id + "/editions"),
            mario,
            EDITION.replace("{", "{\"publisher\":\"  \","))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publisher").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void listSeriesSearchesAndPaginates() throws Exception {
    createEdition(createSeries("One Piece"), EDITION);
    createSeries("Naruto");
    mvc.perform(get("/api/v1/series").param("q", "pie").cookie(mario))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("One Piece"))
        .andExpect(jsonPath("$.content[0].editionCount").value(1));
    mvc.perform(get("/api/v1/series").cookie(mario))
        .andExpect(jsonPath("$.content[0].title").value("Naruto"))
        .andExpect(jsonPath("$.content[0].editionCount").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(2));
    mvc.perform(get("/api/v1/series").param("size", "101").cookie(mario))
        .andExpect(jsonPath("$.page.size").value(100));
  }

  @Test
  void searchTreatsWildcardsLiterally() throws Exception {
    createSeries("100% Manga");
    createSeries("100 Manga");
    mvc.perform(get("/api/v1/series").param("q", "100%").cookie(mario))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("100% Manga"));
  }

  @Test
  void seriesDetailListsEditions() throws Exception {
    var id = createSeries("One Piece");
    createEdition(id, EDITION);
    mvc.perform(get("/api/v1/series/" + id).cookie(mario))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("One Piece"))
        .andExpect(jsonPath("$.editions.length()").value(1))
        .andExpect(jsonPath("$.editions[0].name").value("Edizione blu"))
        .andExpect(jsonPath("$.editions[0].seriesId").value(id));
  }

  @Test
  void nonAdminCannotUpdateOrDelete() throws Exception {
    var s = createSeries("One Piece");
    var e = createEdition(s, EDITION);
    send(put("/api/v1/series/" + s), mario, "{\"title\":\"X\"}").andExpect(status().isForbidden());
    send(delete("/api/v1/series/" + s), mario, null).andExpect(status().isForbidden());
    send(put("/api/v1/editions/" + e), mario, editionJson("X", 0))
        .andExpect(status().isForbidden());
    send(delete("/api/v1/editions/" + e), mario, null).andExpect(status().isForbidden());
  }

  @Test
  void adminUpdatesSeries() throws Exception {
    var s = createSeries("One Piece");
    send(put("/api/v1/series/" + s), admin, "{\"title\":\"One Piece 2\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("One Piece 2"));
  }

  @Test
  void adminUpdatesEditionWithVersion() throws Exception {
    var e = createEdition(createSeries("One Piece"), EDITION);
    send(put("/api/v1/editions/" + e), admin, editionJson("Nuova", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Nuova"))
        .andExpect(jsonPath("$.version").value(1));
    send(put("/api/v1/editions/" + e), admin, editionJson("Ancora", 0))
        .andExpect(status().isConflict());
    send(
            put("/api/v1/editions/" + e),
            admin,
            editionJson("Ancora", 1).replace(",\"version\":1", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
  }

  @Test
  void volumeCountOutOfRangeIs400() throws Exception {
    var s = createSeries("One Piece");
    for (var bad : new String[] {"0", "10000"}) {
      send(post("/api/v1/series/" + s + "/editions"), mario, EDITION.replace("114", bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("volumeCount"));
    }
  }

  @Test
  void unknownTypeIs400() throws Exception {
    var s = createSeries("One Piece");
    send(post("/api/v1/series/" + s + "/editions"), mario, EDITION.replace("REGULAR", "WEIRD"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteSeriesWithEditionsIs409() throws Exception {
    var s = createSeries("One Piece");
    createEdition(s, EDITION);
    send(delete("/api/v1/series/" + s), admin, null).andExpect(status().isConflict());
  }

  @Test
  void deleteEmptySeriesIs204() throws Exception {
    var s = createSeries("One Piece");
    send(delete("/api/v1/series/" + s), admin, null).andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM series", Integer.class)).isZero();
  }

  @Test
  void adminDeletesEditionIs204() throws Exception {
    var e = createEdition(createSeries("One Piece"), EDITION);
    send(delete("/api/v1/editions/" + e), admin, null).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/editions/" + e).cookie(mario)).andExpect(status().isNotFound());
  }

  @Test
  void unknownIdIs404() throws Exception {
    var id = UUID.randomUUID();
    mvc.perform(get("/api/v1/series/" + id).cookie(mario)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/editions/" + id).cookie(mario)).andExpect(status().isNotFound());
    send(post("/api/v1/series/" + id + "/editions"), mario, EDITION)
        .andExpect(status().isNotFound());
    send(delete("/api/v1/series/" + id), admin, null).andExpect(status().isNotFound());
  }

  @Test
  void canEditCoverOnlyForAuthorOrAdmin() throws Exception {
    var e = createEdition(createSeries("One Piece"), EDITION);
    mvc.perform(get("/api/v1/editions/" + e).cookie(admin))
        .andExpect(jsonPath("$.canEditCover").value(true));
    users.save(new AppUser("Luigi", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    mvc.perform(get("/api/v1/editions/" + e).cookie(session("Luigi")))
        .andExpect(jsonPath("$.canEditCover").value(false));
  }
}
