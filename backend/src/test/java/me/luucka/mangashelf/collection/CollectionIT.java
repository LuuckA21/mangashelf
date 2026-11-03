package me.luucka.mangashelf.collection;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class CollectionIT {

  private static final String PASSWORD = "correct-horse-1";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie admin;
  Cookie a;
  Cookie b;

  @BeforeEach
  void seed() throws Exception {
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    users.deleteAll();
    jdbc.update("DELETE FROM spring_session");
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    users.save(new AppUser("Anna", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Bruno", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    admin = session("Admin");
    a = session("Anna");
    b = session("Bruno");
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

  private String read(ResultActions r) throws Exception {
    return r.andReturn().getResponse().getContentAsString();
  }

  private String series(String title) throws Exception {
    return JsonPath.read(
        read(send(post("/api/v1/series"), a, "{\"title\":\"%s\"}".formatted(title))), "$.id");
  }

  private String edition(String seriesId, String name, String type, String status, int volumes)
      throws Exception {
    var json =
        "{\"name\":\"%s\",\"type\":\"%s\",\"status\":\"%s\",\"volumeCount\":%d}"
            .formatted(name, type, status, volumes);
    return JsonPath.read(
        read(send(post("/api/v1/series/" + seriesId + "/editions"), a, json)), "$.id");
  }

  private String edition(String name, int volumes) throws Exception {
    return edition(series("Serie " + name), name, "REGULAR", "ONGOING", volumes);
  }

  private ResultActions owned(Cookie who, String editionId, String json) throws Exception {
    return send(put("/api/v1/collection/editions/" + editionId + "/volumes"), who, json);
  }

  private ResultActions list(Cookie who, String query) throws Exception {
    return mvc.perform(get("/api/v1/collection" + query).cookie(who));
  }

  @Test
  void addEditionIsIdempotent() throws Exception {
    var e = edition("Blu", 10);
    send(put("/api/v1/collection/editions/" + e), a, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editionId").value(e))
        .andExpect(jsonPath("$.owned").isEmpty());
    send(put("/api/v1/collection/editions/" + e), a, null).andExpect(status().isOk());
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void setOwnedVolumesReplacesSet() throws Exception {
    var e = edition("Blu", 10);
    owned(a, e, "{\"owned\":[5,1,2]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owned[0]").value(1))
        .andExpect(jsonPath("$.owned[2]").value(5));
    owned(a, e, "{\"owned\":[2,3]}").andExpect(status().isOk());
    list(a, "")
        .andExpect(jsonPath("$.content[0].owned.length()").value(2))
        .andExpect(jsonPath("$.content[0].owned[0]").value(2))
        .andExpect(jsonPath("$.content[0].owned[1]").value(3));
  }

  @Test
  void settingVolumesAddsEditionToCollection() throws Exception {
    var e = edition("Blu", 10);
    owned(a, e, "{\"owned\":[1]}").andExpect(status().isOk());
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void outOfRangeOrDuplicateVolumesIs400() throws Exception {
    var e = edition("Blu", 114);
    for (var body : new String[] {"{\"owned\":[0]}", "{\"owned\":[115]}", "{\"owned\":[3,3]}"}) {
      owned(a, e, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("owned"));
    }
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void unknownEditionIs404() throws Exception {
    var id = "0198a000-0000-7000-8000-000000000000";
    send(put("/api/v1/collection/editions/" + id), a, null).andExpect(status().isNotFound());
    send(delete("/api/v1/collection/editions/" + id), a, null).andExpect(status().isNotFound());
    owned(a, id, "{\"owned\":[]}").andExpect(status().isNotFound());
  }

  @Test
  void wishWithZeroVolumesIsListedAndIncomplete() throws Exception {
    var wish = edition("Desiderio", 5);
    var full = edition("Completa", 2);
    send(put("/api/v1/collection/editions/" + wish), a, null);
    owned(a, full, "{\"owned\":[1,2]}");
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(2));
    list(a, "?incomplete=true")
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].editionId").value(wish))
        .andExpect(jsonPath("$.content[0].owned").isEmpty());
  }

  @Test
  void filtersByTextTypeAndStatus() throws Exception {
    var berserk = series("Berserk");
    var e1 = edition(berserk, "Deluxe", "VARIANT", "ONGOING", 5);
    var e2 = edition(berserk, "Standard", "REGULAR", "COMPLETED", 5);
    var other = edition(series("100% Pure_Gold"), "Ed", "REGULAR", "ONGOING", 5);
    for (var id : new String[] {e1, e2, other}) {
      send(put("/api/v1/collection/editions/" + id), a, null);
    }
    list(a, "?q=BERSE").andExpect(jsonPath("$.page.totalElements").value(2));
    list(a, "?q=deluxe")
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].editionId").value(e1));
    list(a, "?q=%").andExpect(jsonPath("$.page.totalElements").value(1));
    list(a, "?q=0% pure_")
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].editionId").value(other));
    list(a, "?type=VARIANT").andExpect(jsonPath("$.page.totalElements").value(1));
    list(a, "?status=COMPLETED")
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].editionId").value(e2));
    list(a, "?type=NOPE").andExpect(status().isBadRequest());
    list(a, "?sort=editionName,desc&size=1")
        .andExpect(jsonPath("$.content[0].seriesTitle").value("100% Pure_Gold"))
        .andExpect(jsonPath("$.page.totalPages").value(3));
  }

  @Test
  void collectionsAreIsolated() throws Exception {
    var e = edition("Blu", 10);
    owned(a, e, "{\"owned\":[1]}");
    list(b, "").andExpect(jsonPath("$.page.totalElements").value(0));
    send(delete("/api/v1/collection/editions/" + e), b, null).andExpect(status().isNoContent());
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void removeEditionDeletesItsVolumes() throws Exception {
    var e = edition("Blu", 10);
    owned(a, e, "{\"owned\":[1,2]}");
    send(delete("/api/v1/collection/editions/" + e), a, null).andExpect(status().isNoContent());
    send(delete("/api/v1/collection/editions/" + e), a, null).andExpect(status().isNoContent());
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(0));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM owned_volumes", Integer.class)).isZero();
  }

  @Test
  void lowerVolumeCountBelowOwnedIs409() throws Exception {
    var e = edition("Blu", 114);
    var all = java.util.stream.IntStream.rangeClosed(1, 114).boxed().toList();
    owned(a, e, "{\"owned\":%s}".formatted(all)).andExpect(status().isOk());
    var json =
        "{\"name\":\"Blu\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":%d,\"version\":%d}";
    send(put("/api/v1/editions/" + e), admin, json.formatted(113, 0))
        .andExpect(status().isConflict());
    send(put("/api/v1/editions/" + e), admin, json.formatted(114, 0)).andExpect(status().isOk());
  }

  @Test
  void editionShowsCollectionCountAndMine() throws Exception {
    var s = series("Berserk");
    var e = edition(s, "Blu", "REGULAR", "ONGOING", 10);
    send(put("/api/v1/collection/editions/" + e), a, null);
    send(put("/api/v1/collection/editions/" + e), b, null);
    mvc.perform(get("/api/v1/editions/" + e).cookie(a))
        .andExpect(jsonPath("$.collectionCount").value(2))
        .andExpect(jsonPath("$.inMyCollection").value(true));
    mvc.perform(get("/api/v1/series/" + s).cookie(a))
        .andExpect(jsonPath("$.editions[0].inMyCollection").value(true));
    mvc.perform(get("/api/v1/series/" + s).cookie(admin))
        .andExpect(jsonPath("$.editions[0].inMyCollection").value(false));
  }

  @Test
  void deletingEditionRemovesItFromCollections() throws Exception {
    var e = edition("Blu", 10);
    owned(a, e, "{\"owned\":[1]}");
    send(delete("/api/v1/editions/" + e), admin, null).andExpect(status().isNoContent());
    list(a, "").andExpect(jsonPath("$.page.totalElements").value(0));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM owned_volumes", Integer.class)).isZero();
  }

  @Test
  void deletingUserRemovesTheirCollection() throws Exception {
    var e = edition("Blu", 10);
    owned(b, e, "{\"owned\":[1]}");
    jdbc.update("DELETE FROM users WHERE username = 'Bruno'");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_editions", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM owned_volumes", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM editions", Integer.class)).isEqualTo(1);
  }
}
