package me.luucka.mangashelf.collection;

import static me.luucka.mangashelf.users.AuthTestSupport.send;
import static me.luucka.mangashelf.users.AuthTestSupport.session;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CollectionStatsIT {

  private static final String PASSWORD = "correct-horse-1";

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

  private String series(String title) throws Exception {
    var r = send(mvc, post("/api/v1/series"), a, "{\"title\":\"%s\"}".formatted(title));
    return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private String edition(
      String seriesId, String name, String publisher, String type, String status, int volumes)
      throws Exception {
    var pub = publisher == null ? "null" : "\"%s\"".formatted(publisherId(publisher));
    var json =
        "{\"name\":\"%s\",\"publisherId\":%s,\"type\":\"%s\",\"status\":\"%s\",\"volumeCount\":%d}"
            .formatted(name, pub, type, status, volumes);
    var r = send(mvc, post("/api/v1/series/" + seriesId + "/editions"), a, json);
    return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
  }

  /** Id of the publisher with this name, created on first use. */
  private String publisherId(String name) {
    var ids =
        jdbc.queryForList("SELECT id::text FROM publishers WHERE name = ?", String.class, name);
    if (!ids.isEmpty()) {
      return ids.get(0);
    }
    var id = java.util.UUID.randomUUID();
    jdbc.update("INSERT INTO publishers VALUES (?, ?, now(), now())", id, name);
    return id.toString();
  }

  /** A REGULAR, ONGOING edition in its own series. */
  private String edition(String name, String publisher, int volumes) throws Exception {
    return edition(series("Serie " + name), name, publisher, "REGULAR", "ONGOING", volumes);
  }

  /** Adds the edition to the collection and sets exactly these owned volumes. */
  private void own(Cookie who, String editionId, int... numbers) throws Exception {
    send(mvc, put("/api/v1/collection/editions/" + editionId), who, null)
        .andExpect(status().isOk());
    var list = Arrays.stream(numbers).mapToObj(String::valueOf).collect(Collectors.joining(","));
    send(
            mvc,
            put("/api/v1/collection/editions/" + editionId + "/volumes"),
            who,
            "{\"owned\":[%s]}".formatted(list))
        .andExpect(status().isOk());
  }

  private static int[] upTo(int n) {
    return IntStream.rangeClosed(1, n).toArray();
  }

  private ResultActions stats(Cookie who) throws Exception {
    return mvc.perform(get("/api/v1/collection/stats").cookie(who));
  }

  @Test
  void emptyCollectionIsAllZeros() throws Exception {
    stats(a)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editions").value(0))
        .andExpect(jsonPath("$.series").value(0))
        .andExpect(jsonPath("$.ownedVolumes").value(0))
        .andExpect(jsonPath("$.totalVolumes").value(0))
        .andExpect(jsonPath("$.completedEditions").value(0))
        .andExpect(jsonPath("$.byPublisher").value(empty()))
        .andExpect(jsonPath("$.byType").value(empty()))
        .andExpect(jsonPath("$.byStatus").value(empty()));
  }

  @Test
  void countsEditionsSeriesAndVolumes() throws Exception {
    var berserk = series("Berserk");
    own(a, edition(berserk, "A", "Panini", "REGULAR", "ONGOING", 10), 1, 2, 3);
    own(a, edition(berserk, "B", "Panini", "VARIANT", "COMPLETED", 2));
    own(a, edition(series("Blu"), "C", null, "REGULAR", "COMPLETED", 4), upTo(4));

    stats(a)
        .andExpect(jsonPath("$.editions").value(3))
        .andExpect(jsonPath("$.series").value(2))
        .andExpect(jsonPath("$.ownedVolumes").value(7))
        .andExpect(jsonPath("$.totalVolumes").value(16))
        .andExpect(jsonPath("$.completedEditions").value(1))
        .andExpect(jsonPath("$.byType[*].key").value(contains("REGULAR")))
        .andExpect(jsonPath("$.byType[0].volumes").value(7))
        .andExpect(jsonPath("$.byStatus[*].key").value(contains("ONGOING", "COMPLETED")))
        .andExpect(jsonPath("$.byStatus[*].volumes").value(contains(3, 4)))
        .andExpect(jsonPath("$.byPublisher.length()").value(2))
        .andExpect(jsonPath("$.byPublisher[0].key").value("Panini"))
        .andExpect(jsonPath("$.byPublisher[0].volumes").value(3))
        .andExpect(jsonPath("$.byPublisher[1].key").value(nullValue()))
        .andExpect(jsonPath("$.byPublisher[1].volumes").value(4));
  }

  @Test
  void ongoingWithAllVolumesIsNotCompleted() throws Exception {
    own(a, edition("Blu", "Panini", 3), upTo(3));
    stats(a)
        .andExpect(jsonPath("$.ownedVolumes").value(3))
        .andExpect(jsonPath("$.completedEditions").value(0));
  }

  @Test
  void topFivePublishersPlusOthers() throws Exception {
    own(a, edition("E1", "P1", 6), upTo(6));
    own(a, edition("E2", "P2", 5), upTo(5));
    own(a, edition("E3", "P3", 4), upTo(4));
    own(a, edition("E4", "Ab", 3), upTo(3));
    own(a, edition("E5", "Aa", 3), upTo(3));
    own(a, edition("E6", "P6", 2), upTo(2));
    own(a, edition("E7", null, 1), upTo(1));

    stats(a)
        .andExpect(jsonPath("$.byPublisher.length()").value(6))
        .andExpect(jsonPath("$.byPublisher[0:5].key").value(contains("P1", "P2", "P3", "Aa", "Ab")))
        .andExpect(jsonPath("$.byPublisher[*].volumes").value(contains(6, 5, 4, 3, 3, 3)))
        .andExpect(jsonPath("$.byPublisher[5].key").value(nullValue()));
  }

  @Test
  void zeroEntriesAndEmptyOthersAreOmitted() throws Exception {
    own(a, edition("E1", "Solo", 2), upTo(2));
    own(a, edition("E2", "Zero", 5));
    stats(a)
        .andExpect(jsonPath("$.editions").value(2))
        .andExpect(jsonPath("$.byPublisher[*].key").value(contains("Solo")))
        .andExpect(jsonPath("$.byPublisher[0].volumes").value(2));
  }

  @Test
  void otherUsersAreNotCounted() throws Exception {
    own(b, edition("Blu", "Panini", 5), upTo(5));
    stats(a)
        .andExpect(jsonPath("$.editions").value(0))
        .andExpect(jsonPath("$.ownedVolumes").value(0));
    stats(b).andExpect(jsonPath("$.ownedVolumes").value(5));
  }

  @Test
  void requiresLogin() throws Exception {
    mvc.perform(get("/api/v1/collection/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void isForbiddenWhileThePasswordMustChange() throws Exception {
    users.save(new AppUser("Carla", encoder.encode(PASSWORD), Role.USER, true, Instant.now()));
    stats(session(mvc, "Carla", PASSWORD)).andExpect(status().isForbidden());
  }
}
