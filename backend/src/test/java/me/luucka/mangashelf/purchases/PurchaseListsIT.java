package me.luucka.mangashelf.purchases;

import static me.luucka.mangashelf.users.AuthTestSupport.cookie;
import static me.luucka.mangashelf.users.AuthTestSupport.login;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
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
class PurchaseListsIT {

  private static final String PASSWORD = "correct-horse-1";
  private static final String URL = "/api/v1/purchase-lists";

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
            "{\"name\":\"Deluxe\",\"publisher\":null,\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":10}");
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

  @Test
  void createAndListOrderedByMonthDesc() throws Exception {
    list("Agosto", 8, 2026);
    list("Ottobre", 10, 2026);
    var sep = list("Settembre", 9, 2026);
    item(sep, 1, null, 790, 950);
    item(sep, 2, null, 995, 1190).andExpect(status().isCreated());
    send(patch(URL + "/" + sep + "/items/" + firstItem(sep)), a, "{\"booked\":true}");

    mvc.perform(get(URL).cookie(a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].description").value(contains("Ottobre", "Settembre", "Agosto")))
        .andExpect(jsonPath("$[1].itemCount").value(2))
        .andExpect(jsonPath("$[1].unbookedCount").value(1))
        // subtotal 2140, automatic discount 214 -> 210
        .andExpect(jsonPath("$[1].totalCents").value(1930))
        .andExpect(jsonPath("$[0].totalCents").value(0));
    mvc.perform(get(URL).cookie(b)).andExpect(jsonPath("$.length()").value(0));
  }

  private String firstItem(String listId) throws Exception {
    return json(mvc.perform(get(URL + "/" + listId).cookie(a)), "$.items[0].id");
  }

  @Test
  void listValidation() throws Exception {
    send(post(URL), a, "{\"description\":\"  \",\"month\":13,\"year\":1999}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("description", "month", "year")));
    var id = list("Ok", 1, 2026);
    patchList(id, "{\"description\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("description"));
  }

  @Test
  void rowValidationAndDefaults() throws Exception {
    var id = list("Ok", 1, 2026);
    send(
            post(URL + "/" + id + "/items"),
            a,
            "{\"editionId\":\"%s\",\"volume\":1,\"priceChfCents\":100}".formatted(edition))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("priceEurCents"));
    item(id, 1, null, 100, 120)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].booked").value(false))
        .andExpect(jsonPath("$.items[0].releaseDate").value(nullValue()))
        .andExpect(jsonPath("$.items[0].seriesTitle").value("Berserk"))
        .andExpect(jsonPath("$.items[0].editionName").value("Deluxe"))
        .andExpect(jsonPath("$.items[0].editionVolumeCount").value(10))
        .andExpect(jsonPath("$.items[0].alsoIn.length()").value(0));
    send(
            post(URL + "/" + id + "/items"),
            a,
            "{\"editionId\":\"%s\",\"volume\":1,\"priceEurCents\":1,\"priceChfCents\":1}"
                .formatted("00000000-0000-7000-8000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void rowsOrderedByDateThenTitle() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 5, null, 100, 100);
    item(id, 3, "2026-02-01", 100, 100);
    item(id, 2, "2026-01-01", 100, 100);
    item(id, 1, "2026-01-01", 100, 100);
    mvc.perform(get(URL + "/" + id).cookie(a))
        .andExpect(jsonPath("$.items[*].volume").value(contains(1, 2, 3, 5)));
  }

  @Test
  void totalsWithAutomaticDiscount() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 7900, 9500);
    item(id, 2, null, 2590, 3110)
        .andExpect(jsonPath("$.subtotalCents").value(12610))
        .andExpect(jsonPath("$.discountCents").value(1260))
        .andExpect(jsonPath("$.discountAuto").value(true))
        .andExpect(jsonPath("$.totalCents").value(11350));
  }

  @Test
  void fixedDiscountStaysWhenRowsChange() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 9500);
    patchList(id, "{\"discountCents\":500}")
        .andExpect(jsonPath("$.discountCents").value(500))
        .andExpect(jsonPath("$.discountAuto").value(false));
    item(id, 2, null, 100, 3110)
        .andExpect(jsonPath("$.discountCents").value(500))
        .andExpect(jsonPath("$.discountAuto").value(false))
        .andExpect(jsonPath("$.totalCents").value(12110));
    patchList(id, "{\"discountAuto\":true}")
        .andExpect(jsonPath("$.discountCents").value(1260))
        .andExpect(jsonPath("$.discountAuto").value(true));
  }

  @Test
  void pointsDiscountReducesTotal() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 9500);
    patchList(id, "{\"pointsDiscountCents\":300}")
        .andExpect(jsonPath("$.pointsDiscountCents").value(300))
        .andExpect(jsonPath("$.totalCents").value(9500 - 950 - 300));
  }

  @Test
  void discountAboveSubtotalIs400() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 950);
    patchList(id, "{\"discountCents\":951}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("discountCents"))
        .andExpect(jsonPath("$.errors[0].message").value("Lo sconto supera il subtotale"));
    patchList(id, "{\"pointsDiscountCents\":900}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("pointsDiscountCents"));
    // rejected: nothing was saved
    mvc.perform(get(URL + "/" + id).cookie(a))
        .andExpect(jsonPath("$.pointsDiscountCents").value(0))
        .andExpect(jsonPath("$.discountAuto").value(true));
  }

  @Test
  void totalNeverBelowZeroAfterRowsRemoved() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 950);
    patchList(id, "{\"discountCents\":900}").andExpect(status().isOk());
    send(delete(URL + "/" + id + "/items/" + firstItem(id)), a, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.discountCents").value(900))
        .andExpect(jsonPath("$.totalCents").value(0));
  }

  @Test
  void keptDiscountDoesNotBlockUnrelatedPatch() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 950);
    patchList(id, "{\"discountCents\":900,\"pointsDiscountCents\":50}").andExpect(status().isOk());
    send(delete(URL + "/" + id + "/items/" + firstItem(id)), a, null).andExpect(status().isOk());
    patchList(id, "{\"description\":\"x\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("x"))
        .andExpect(jsonPath("$.discountCents").value(900))
        .andExpect(jsonPath("$.pointsDiscountCents").value(50))
        .andExpect(jsonPath("$.totalCents").value(0));
    patchList(id, "{\"discountAuto\":true}").andExpect(status().isOk());
  }

  @Test
  void deleteOpenListRemovesRows() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 100);
    send(delete(URL + "/" + id), a, null).andExpect(status().isNoContent());
    mvc.perform(get(URL + "/" + id).cookie(a)).andExpect(status().isNotFound());
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT count(*) FROM purchase_items", Long.class))
        .isZero();
  }

  @Test
  void closedListCannotBeDeleted() throws Exception {
    var id = list("Ok", 1, 2026);
    jdbc.update("UPDATE purchase_lists SET closed = true");
    send(delete(URL + "/" + id), a, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Una lista chiusa non si può eliminare"));
  }

  @Test
  void otherUsersListIs404() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, null, 100, 100);
    var itemId = firstItem(id);
    mvc.perform(get(URL + "/" + id).cookie(b)).andExpect(status().isNotFound());
    send(patch(URL + "/" + id), b, "{\"month\":2}").andExpect(status().isNotFound());
    send(delete(URL + "/" + id), b, null).andExpect(status().isNotFound());
    send(
            post(URL + "/" + id + "/items"),
            b,
            "{\"editionId\":\"%s\",\"volume\":1,\"priceEurCents\":1,\"priceChfCents\":1}"
                .formatted(edition))
        .andExpect(status().isNotFound());
    send(patch(URL + "/" + id + "/items/" + itemId), b, "{\"booked\":true}")
        .andExpect(status().isNotFound());
    send(delete(URL + "/" + id + "/items/" + itemId), b, null).andExpect(status().isNotFound());
  }

  @Test
  void itemOfAnotherListIs404() throws Exception {
    var listA = list("A", 1, 2026);
    var listB = list("B", 2, 2026);
    item(listB, 1, null, 100, 100);
    var itemOfB = firstItem(listB);
    send(patch(URL + "/" + listA + "/items/" + itemOfB), a, "{\"booked\":true}")
        .andExpect(status().isNotFound());
    send(delete(URL + "/" + listA + "/items/" + itemOfB), a, null).andExpect(status().isNotFound());
  }

  @Test
  void patchItemClearsDateAndChangesPrice() throws Exception {
    var id = list("Ok", 1, 2026);
    item(id, 1, "2026-01-01", 100, 100);
    send(
            patch(URL + "/" + id + "/items/" + firstItem(id)),
            a,
            "{\"clearReleaseDate\":true,\"priceChfCents\":250,\"booked\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].releaseDate").value(nullValue()))
        .andExpect(jsonPath("$.items[0].priceChfCents").value(250))
        .andExpect(jsonPath("$.items[0].priceEurCents").value(100))
        .andExpect(jsonPath("$.items[0].booked").value(true));
  }

  @Test
  void requiresLogin() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }
}
