package me.luucka.mangashelf.purchases;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class PurchaseSummaryIT {

  private static final String PASSWORD = "correct-horse-1";
  private static final String URL = "/api/v1/purchase-lists";
  private static final String SUMMARY = "/api/v1/purchase-summary";

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie a;
  Cookie b;
  String edition;

  @BeforeEach
  void seed() throws Exception {
    clean();
    users.save(new AppUser("Anna", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Bruno", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    a = session("Anna");
    b = session("Bruno");
    var s = send(post("/api/v1/series"), a, "{\"title\":\"Berserk\"}");
    var sid = JsonPath.read(s.andReturn().getResponse().getContentAsString(), "$.id");
    var e =
        send(
            post("/api/v1/series/" + sid + "/editions"),
            a,
            "{\"name\":\"Deluxe\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":10}");
    edition = JsonPath.read(e.andReturn().getResponse().getContentAsString(), "$.id");
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

  private String json(ResultActions r, String path) throws Exception {
    return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
  }

  private String list(String description, int month, int year) throws Exception {
    var r =
        send(
                post(URL),
                a,
                "{\"description\":\"%s\",\"month\":%d,\"year\":%d}"
                    .formatted(description, month, year))
            .andExpect(status().isCreated());
    return json(r, "$.id");
  }

  private ResultActions item(String listId, int volume, String date, long eur, long chf)
      throws Exception {
    var d = date == null ? "null" : "\"" + date + "\"";
    return send(
        post(URL + "/" + listId + "/items"),
        a,
        "{\"editionId\":\"%s\",\"volume\":%d,\"releaseDate\":%s,\"priceEurCents\":%d,\"priceChfCents\":%d}"
            .formatted(edition, volume, d, eur, chf));
  }

  private ResultActions patchList(String id, String body) throws Exception {
    return send(patch(URL + "/" + id), a, body);
  }

  /** A list with one row of the given CHF price and no discount. */
  private String paid(String description, int month, int year, long chf, boolean closed)
      throws Exception {
    var id = list(description, month, year);
    item(id, 1, null, 100, chf);
    patchList(id, "{\"discountCents\":0}").andExpect(status().isOk());
    if (closed) {
      send(post(URL + "/" + id + "/close"), a, null).andExpect(status().isOk());
    }
    return id;
  }

  private ResultActions summary(String query) throws Exception {
    return mvc.perform(get(SUMMARY + query).cookie(a));
  }

  @Test
  void noListsIsEmpty() throws Exception {
    summary("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.years.length()").value(0))
        .andExpect(jsonPath("$.year").value(nullValue()))
        .andExpect(jsonPath("$.byYear.length()").value(0))
        .andExpect(jsonPath("$.months.length()").value(12))
        .andExpect(jsonPath("$.months[0].month").value(1))
        .andExpect(jsonPath("$.months[11].month").value(12))
        .andExpect(jsonPath("$.months[*].hasLists").value(everyItem(is(false))))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(0))
        .andExpect(jsonPath("$.monthTotals.averageCents").value(0))
        .andExpect(jsonPath("$.yearTotals.totalCents").value(0))
        .andExpect(jsonPath("$.yearTotals.averageCents").value(0));
  }

  @Test
  void closedIsSpentOpenIsUpcoming() throws Exception {
    paid("Chiusa", 3, 2026, 1000, true);
    paid("Aperta", 3, 2026, 250, false);
    paid("Altra", 4, 2026, 70, false);
    summary("?year=2026")
        .andExpect(jsonPath("$.months[2].spentCents").value(1000))
        .andExpect(jsonPath("$.months[2].upcomingCents").value(250))
        .andExpect(jsonPath("$.months[2].hasLists").value(true))
        .andExpect(jsonPath("$.months[3].spentCents").value(0))
        .andExpect(jsonPath("$.months[3].upcomingCents").value(70))
        .andExpect(jsonPath("$.monthTotals.spentCents").value(1000))
        .andExpect(jsonPath("$.monthTotals.upcomingCents").value(320))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(1320))
        .andExpect(jsonPath("$.byYear[0].spentCents").value(1000))
        .andExpect(jsonPath("$.byYear[0].upcomingCents").value(320));
  }

  @Test
  void listTotalAfterDiscountsIsTheAmount() throws Exception {
    var id = list("Sconto", 5, 2026);
    item(id, 1, null, 100, 10000);
    summary("?year=2026")
        .andExpect(jsonPath("$.months[4].upcomingCents").value(9000))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(9000));
  }

  @Test
  void clampedListCountsZero() throws Exception {
    var id = paid("Vuota", 6, 2026, 950, false);
    patchList(id, "{\"discountCents\":900,\"pointsDiscountCents\":50}").andExpect(status().isOk());
    send(delete(URL + "/" + id + "/items/" + firstItem(id)), a, null).andExpect(status().isOk());
    summary("?year=2026")
        .andExpect(jsonPath("$.months[5].hasLists").value(true))
        .andExpect(jsonPath("$.months[5].upcomingCents").value(0))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(0))
        .andExpect(jsonPath("$.yearTotals.totalCents").value(0));
  }

  private String firstItem(String listId) throws Exception {
    return json(mvc.perform(get(URL + "/" + listId).cookie(a)), "$.items[0].id");
  }

  @Test
  void zeroMonthWithListsShowsButIsNotAveraged() throws Exception {
    paid("Uno", 1, 2026, 10000, true);
    paid("Due", 2, 2026, 5000, true);
    var id = paid("Zero", 3, 2026, 100, true);
    patchList(id, "{\"discountCents\":0}");
    send(delete(URL + "/" + id + "/items/" + firstItem(id)), a, null).andExpect(status().isOk());
    summary("?year=2026")
        .andExpect(jsonPath("$.months[2].hasLists").value(true))
        .andExpect(jsonPath("$.months[2].spentCents").value(0))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(15000))
        .andExpect(jsonPath("$.monthTotals.averageCents").value(7500))
        .andExpect(jsonPath("$.months[0].aboveAverage").value(true))
        .andExpect(jsonPath("$.months[1].aboveAverage").value(false))
        .andExpect(jsonPath("$.months[2].aboveAverage").value(false));
  }

  @Test
  void averageRoundsHalfUp() throws Exception {
    paid("Uno", 1, 2026, 10001, true);
    paid("Due", 2, 2026, 10000, true);
    summary("?year=2026").andExpect(jsonPath("$.monthTotals.averageCents").value(10001));
  }

  @Test
  void aboveAverageIsStrictlyGreater() throws Exception {
    paid("Uno", 1, 2026, 500, true);
    paid("Due", 2, 2026, 500, false);
    summary("?year=2026")
        .andExpect(jsonPath("$.monthTotals.averageCents").value(500))
        .andExpect(jsonPath("$.months[0].aboveAverage").value(false))
        .andExpect(jsonPath("$.months[1].aboveAverage").value(false));
  }

  @Test
  void yearsAscendingWithTheirAverage() throws Exception {
    paid("Nuova", 1, 2026, 3000, true);
    paid("Vecchia", 1, 2025, 1000, true);
    summary("")
        .andExpect(jsonPath("$.years").value(contains(2025, 2026)))
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.byYear[*].year").value(contains(2025, 2026)))
        .andExpect(jsonPath("$.byYear[0].aboveAverage").value(false))
        .andExpect(jsonPath("$.byYear[1].aboveAverage").value(true))
        .andExpect(jsonPath("$.yearTotals.totalCents").value(4000))
        .andExpect(jsonPath("$.yearTotals.averageCents").value(2000))
        .andExpect(jsonPath("$.yearTotals.spentCents").value(4000));
  }

  @Test
  void requestedYearWithoutListsFallsBackToMostRecent() throws Exception {
    paid("Nuova", 1, 2026, 3000, true);
    paid("Vecchia", 1, 2025, 1000, true);
    summary("?year=2024")
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(3000));
    summary("?year=2025")
        .andExpect(jsonPath("$.year").value(2025))
        .andExpect(jsonPath("$.monthTotals.totalCents").value(1000));
  }

  @Test
  void missingYearFallsBackToMostRecent() throws Exception {
    paid("Vecchia", 1, 2025, 1000, true);
    paid("Nuova", 1, 2026, 3000, true);
    summary("").andExpect(jsonPath("$.year").value(2026));
  }

  @Test
  void yearOutOfRangeIs400() throws Exception {
    summary("?year=1999").andExpect(status().isBadRequest());
    summary("?year=2101").andExpect(status().isBadRequest());
  }

  @Test
  void otherUsersListsNotCounted() throws Exception {
    paid("Mia", 1, 2026, 1000, true);
    mvc.perform(get(SUMMARY).cookie(b))
        .andExpect(jsonPath("$.year").value(nullValue()))
        .andExpect(jsonPath("$.yearTotals.totalCents").value(0));
  }

  @Test
  void requiresLogin() throws Exception {
    mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
  }

  @Test
  void isForbiddenWhileThePasswordMustChange() throws Exception {
    users.save(new AppUser("Carla", encoder.encode(PASSWORD), Role.USER, true, Instant.now()));
    mvc.perform(get(SUMMARY).cookie(session("Carla"))).andExpect(status().isForbidden());
  }
}
