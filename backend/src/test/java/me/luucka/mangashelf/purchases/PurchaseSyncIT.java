package me.luucka.mangashelf.purchases;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import me.luucka.mangashelf.TestcontainersConfig;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.AppUserRepository;
import me.luucka.mangashelf.users.Role;
import org.junit.jupiter.api.AfterEach;
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
class PurchaseSyncIT {

  private static final String PASSWORD = "correct-horse-1";
  private static final String URL = "/api/v1/purchase-lists";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie a;
  Cookie b;
  Cookie admin;
  String seriesId;
  String edition;

  @BeforeEach
  void seed() throws Exception {
    clean();
    users.save(new AppUser("Anna", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Bruno", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    a = session("Anna");
    b = session("Bruno");
    admin = session("Admin");
    seriesId = json(send(post("/api/v1/series"), a, "{\"title\":\"Berserk\"}"), "$.id");
    edition = edition("Deluxe", 10);
  }

  @AfterEach
  void clean() {
    // users first: their lists and rows cascade, and rows restrict deleting editions
    users.deleteAll();
    jdbc.update("DELETE FROM editions");
    jdbc.update("DELETE FROM series");
    jdbc.update("DELETE FROM spring_session");
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

  private static <T> T json(ResultActions r, String path) throws Exception {
    return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
  }

  private String edition(String name, int volumeCount) throws Exception {
    return json(
        send(
            post("/api/v1/series/" + seriesId + "/editions"),
            a,
            "{\"name\":\"%s\",\"publisher\":null,\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":%d}"
                .formatted(name, volumeCount)),
        "$.id");
  }

  private String list(Cookie who, String description) throws Exception {
    var r =
        send(
                post(URL),
                who,
                "{\"description\":\"%s\",\"month\":8,\"year\":2026}".formatted(description))
            .andExpect(status().isCreated());
    return json(r, "$.id");
  }

  private String list(String description) throws Exception {
    return list(a, description);
  }

  private ResultActions item(
      Cookie who,
      String listId,
      String editionId,
      int volume,
      String date,
      long eur,
      boolean booked)
      throws Exception {
    var d = date == null ? "null" : "\"" + date + "\"";
    return send(
        post(URL + "/" + listId + "/items"),
        who,
        "{\"editionId\":\"%s\",\"volume\":%d,\"releaseDate\":%s,\"priceEurCents\":%d,\"priceChfCents\":%d,\"booked\":%b}"
            .formatted(editionId, volume, d, eur, eur * 12 / 10, booked));
  }

  private ResultActions item(String listId, int volume, boolean booked) throws Exception {
    return item(a, listId, edition, volume, null, 100, booked).andExpect(status().isCreated());
  }

  private String itemId(String listId, int volume) throws Exception {
    List<String> ids =
        json(
            mvc.perform(get(URL + "/" + listId).cookie(a)),
            "$.items[?(@.volume==" + volume + ")].id");
    return ids.getFirst();
  }

  private ResultActions close(String listId) throws Exception {
    return send(post(URL + "/" + listId + "/close"), a, null);
  }

  private ResultActions reopen(String listId) throws Exception {
    return send(post(URL + "/" + listId + "/reopen"), a, null);
  }

  private ResultActions patchItem(String listId, int volume, String body) throws Exception {
    return send(patch(URL + "/" + listId + "/items/" + itemId(listId, volume)), a, body);
  }

  private ResultActions move(Cookie who, String from, List<String> itemIds, String to, String date)
      throws Exception {
    var ids = itemIds.stream().map(i -> "\"" + i + "\"").toList();
    var d = date == null ? "null" : "\"" + date + "\"";
    return send(
        post(URL + "/" + from + "/items/move"),
        who,
        "{\"itemIds\":%s,\"targetListId\":\"%s\",\"releaseDate\":%s}".formatted(ids, to, d));
  }

  private void handTick(Integer... owned) throws Exception {
    send(
            put("/api/v1/collection/editions/" + edition + "/volumes"),
            a,
            "{\"owned\":%s}".formatted(List.of(owned)))
        .andExpect(status().isOk());
  }

  /** Anna's owned volumes of the default edition, ascending (Bruno never owns anything here). */
  private List<Integer> owned() {
    return jdbc.queryForList(
        """
        SELECT ov.number FROM owned_volumes ov
        JOIN collection_editions ce ON ce.id = ov.collection_edition_id
        WHERE ce.edition_id = ?::uuid ORDER BY ov.number
        """,
        Integer.class,
        edition);
  }

  private long editionVersion(String id) throws Exception {
    return ((Number) json(mvc.perform(get("/api/v1/editions/" + id).cookie(admin)), "$.version"))
        .longValue();
  }

  @Test
  void closeMarksOnlyBookedAsOwned() throws Exception {
    var l = list("Agosto");
    item(l, 1, true);
    item(l, 2, false);
    close(l).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true));
    assertThat(owned()).containsExactly(1);
  }

  @Test
  void reopenMakesThemMissing() throws Exception {
    var l = list("Agosto");
    item(l, 1, true);
    close(l).andExpect(status().isOk());
    reopen(l).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(false));
    assertThat(owned()).isEmpty();
  }

  @Test
  void closeTwiceAfterReopenKeepsOneOwnedRow() throws Exception {
    var l = list("Agosto");
    item(l, 1, true);
    handTick(1);
    close(l).andExpect(status().isOk());
    close(l).andExpect(status().isOk());
    reopen(l).andExpect(status().isOk());
    close(l).andExpect(status().isOk());
    assertThat(owned()).containsExactly(1);
  }

  @Test
  void addBookedRowToClosedListOwnsIt() throws Exception {
    var l = list("Agosto");
    close(l).andExpect(status().isOk());
    item(l, 3, true);
    item(l, 4, false);
    assertThat(owned()).containsExactly(3);
  }

  @Test
  void bookingInClosedListOwnsIt() throws Exception {
    var l = list("Agosto");
    item(l, 2, false);
    close(l).andExpect(status().isOk());
    assertThat(owned()).isEmpty();
    patchItem(l, 2, "{\"booked\":true}").andExpect(status().isOk());
    assertThat(owned()).containsExactly(2);
  }

  @Test
  void unbookingInClosedListMakesMissing() throws Exception {
    var l = list("Agosto");
    item(l, 2, true);
    close(l).andExpect(status().isOk());
    assertThat(owned()).containsExactly(2);
    patchItem(l, 2, "{\"booked\":false}").andExpect(status().isOk());
    assertThat(owned()).isEmpty();
  }

  @Test
  void deleteBookedRowFromClosedListMakesMissing() throws Exception {
    var l = list("Agosto");
    item(l, 2, true);
    close(l).andExpect(status().isOk());
    send(delete(URL + "/" + l + "/items/" + itemId(l, 2)), a, null).andExpect(status().isOk());
    assertThat(owned()).isEmpty();
  }

  @Test
  void missingEvenIfTickedByHand() throws Exception {
    handTick(5);
    var l = list("Agosto");
    item(l, 5, true);
    close(l).andExpect(status().isOk());
    send(delete(URL + "/" + l + "/items/" + itemId(l, 5)), a, null).andExpect(status().isOk());
    assertThat(owned()).isEmpty();
  }

  @Test
  void stillOwnedViaAnotherClosedList() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    item(l1, 5, true);
    item(l2, 5, true);
    close(l1).andExpect(status().isOk());
    close(l2).andExpect(status().isOk());
    reopen(l1).andExpect(status().isOk());
    assertThat(owned()).containsExactly(5);
    send(delete(URL + "/" + l2 + "/items/" + itemId(l2, 5)), a, null).andExpect(status().isOk());
    assertThat(owned()).isEmpty();
  }

  @Test
  void moveBookedOutOfClosedListMakesMissing() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    item(l1, 5, true);
    close(l1).andExpect(status().isOk());
    move(a, l1, List.of(itemId(l1, 5)), l2, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(l1))
        .andExpect(jsonPath("$.items").value(empty()));
    mvc.perform(get(URL + "/" + l2).cookie(a))
        .andExpect(jsonPath("$.items[*].volume").value(contains(5)));
    assertThat(owned()).isEmpty();
  }

  @Test
  void moveBookedBetweenClosedListsStaysOwned() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    item(l1, 5, true);
    close(l1).andExpect(status().isOk());
    close(l2).andExpect(status().isOk());
    move(a, l1, List.of(itemId(l1, 5)), l2, null).andExpect(status().isOk());
    assertThat(owned()).containsExactly(5);
  }

  @Test
  void moveAppliesNewDateOnlyWhenGiven() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    item(a, l1, edition, 1, "2026-08-20", 100, false).andExpect(status().isCreated());
    item(a, l1, edition, 2, null, 100, false).andExpect(status().isCreated());
    item(a, l1, edition, 3, "2026-08-21", 100, false).andExpect(status().isCreated());
    move(a, l1, List.of(itemId(l1, 1)), l2, null).andExpect(status().isOk());
    move(a, l1, List.of(itemId(l1, 2), itemId(l1, 3)), l2, "2026-09-10")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").value(empty()));
    mvc.perform(get(URL + "/" + l2).cookie(a))
        .andExpect(jsonPath("$.items[*].volume").value(contains(1, 2, 3)))
        .andExpect(
            jsonPath("$.items[*].releaseDate")
                .value(contains("2026-08-20", "2026-09-10", "2026-09-10")));
  }

  @Test
  void editingVolumeOfBookedRowInClosedListMovesOwnership() throws Exception {
    var l = list("Agosto");
    item(l, 5, true);
    close(l).andExpect(status().isOk());
    patchItem(l, 5, "{\"volume\":6}").andExpect(status().isOk());
    assertThat(owned()).containsExactly(6);
  }

  @Test
  void closingAddsEditionToCollection() throws Exception {
    mvc.perform(get("/api/v1/collection/editions/" + edition).cookie(a))
        .andExpect(status().isNotFound());
    var l = list("Agosto");
    item(l, 1, true);
    close(l).andExpect(status().isOk());
    mvc.perform(get("/api/v1/collection/editions/" + edition).cookie(a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owned").value(contains(1)));
  }

  @Test
  void rowAboveVolumeCountRaisesCatalog() throws Exception {
    var big = edition("Big", 113);
    var l = list("Agosto");
    item(a, l, big, 114, null, 100, false)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].editionVolumeCount").value(114));
    mvc.perform(get("/api/v1/editions/" + big).cookie(a))
        .andExpect(jsonPath("$.volumeCount").value(114));
    send(
            patch(
                URL
                    + "/"
                    + l
                    + "/items/"
                    + json(mvc.perform(get(URL + "/" + l).cookie(a)), "$.items[0].id")),
            a,
            "{\"volume\":120}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].editionVolumeCount").value(120));
  }

  @Test
  void deleteEditionUsedByRowIs409() throws Exception {
    var l = list("Agosto");
    item(l, 1, false);
    send(delete("/api/v1/editions/" + edition), admin, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("L'edizione è in una lista acquisti"));
  }

  @Test
  void loweringVolumeCountBelowARowIs409() throws Exception {
    var l = list("Agosto");
    item(l, 8, false);
    var body =
        "{\"name\":\"Deluxe\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":%d,\"version\":%d}";
    send(put("/api/v1/editions/" + edition), admin, body.formatted(7, editionVersion(edition)))
        .andExpect(status().isConflict());
    send(put("/api/v1/editions/" + edition), admin, body.formatted(8, editionVersion(edition)))
        .andExpect(status().isOk());
  }

  @Test
  void alsoInListsOtherListsWithSameVolume() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    var other = list(b, "Di Bruno");
    item(l1, 5, false);
    item(l2, 5, false);
    item(l2, 6, false);
    item(b, other, edition, 6, null, 100, false).andExpect(status().isCreated());
    mvc.perform(get(URL + "/" + l2).cookie(a))
        .andExpect(jsonPath("$.items[0].alsoIn").value(contains("Agosto")))
        .andExpect(jsonPath("$.items[1].alsoIn").value(empty()));
  }

  @Test
  void proposalNextVolumeLastEurAndListDate() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    mvc.perform(get(URL + "/" + l1 + "/proposal").param("editionId", edition).cookie(a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.volume").value(1))
        .andExpect(jsonPath("$.priceEurCents").value(nullValue()))
        .andExpect(jsonPath("$.releaseDate").value(nullValue()));
    handTick(3);
    item(a, l1, edition, 5, "2026-08-20", 590, false).andExpect(status().isCreated());
    item(a, l2, edition, 2, "2026-09-10", 610, false).andExpect(status().isCreated());
    // Bruno's rows never count for Anna
    var other = list(b, "Di Bruno");
    item(b, other, edition, 9, "2026-10-01", 999, false).andExpect(status().isCreated());
    mvc.perform(get(URL + "/" + l1 + "/proposal").param("editionId", edition).cookie(a))
        .andExpect(jsonPath("$.volume").value(6))
        .andExpect(jsonPath("$.priceEurCents").value(610))
        .andExpect(jsonPath("$.releaseDate").value("2026-08-20"));
    handTick(3, 9);
    mvc.perform(get(URL + "/" + l1 + "/proposal").param("editionId", edition).cookie(a))
        .andExpect(jsonPath("$.volume").value(10));
    mvc.perform(get(URL + "/" + l1 + "/proposal").param("editionId", edition).cookie(b))
        .andExpect(status().isNotFound());
  }

  @Test
  void moveToSameListIs400() throws Exception {
    var l = list("Agosto");
    item(l, 1, false);
    move(a, l, List.of(itemId(l, 1)), l, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("targetListId"));
  }

  @Test
  void moveIntoAnotherUsersListIs404() throws Exception {
    var l1 = list("Agosto");
    var l2 = list("Settembre");
    var other = list(b, "Di Bruno");
    item(l1, 1, false);
    item(l2, 2, false);
    move(a, l1, List.of(itemId(l1, 1)), other, null).andExpect(status().isNotFound());
    // an item of another list cannot be moved through this one
    move(a, l1, List.of(itemId(l2, 2)), other, null).andExpect(status().isNotFound());
    move(a, l1, List.of(itemId(l2, 2)), l2, null).andExpect(status().isNotFound());
    mvc.perform(get(URL + "/" + l1).cookie(a))
        .andExpect(jsonPath("$.items[*].volume").value(contains(1)));
    mvc.perform(get(URL + "/" + other).cookie(b)).andExpect(jsonPath("$.items").value(empty()));
  }
}
