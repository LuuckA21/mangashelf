package me.luucka.mangashelf;

import me.luucka.mangashelf.user.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Purchase lists: totals, suggestions, and moving a list onto the shelf. */
class PurchaseIT extends IntegrationTest {

    /**
     * The insert used to wait for the end of the transaction, so the new line
     * came back with a null id — which the browser then could neither edit
     * nor delete.
     */
    @Test
    void aNewLineComesBackWithItsId() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");

        mvc.perform(addLine(list, series, 114, 690, 830))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").isNumber());
    }

    @Test
    void duplicateLinesAreRejectedWhenAddedOrEdited() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");
        mvc.perform(addLine(list, series, 1, 690, 830))
                .andExpect(status().isOk());

        mvc.perform(addLine(list, series, 1, 700, 900))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item_already_on_list"));

        String body = json(addLine(list, series, 2, 700, 900), 200);
        long second = itemId(body, 1);
        mvc.perform(put("/api/purchases/" + list + "/items/" + second)
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"seriesId": %d, "volumeNumber": 1,
                                 "priceEurCents": 700, "priceChfCents": 900}
                                """.formatted(series)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item_already_on_list"));

        mvc.perform(get("/api/purchases/" + list).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].volumeNumber").value(2));
    }

    @Test
    void totalsAddUpWithAPercentageDiscount() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");

        mvc.perform(addLine(list, series, 1, 690, 830));
        mvc.perform(addLine(list, series, 2, 690, 830));

        mvc.perform(put("/api/purchases/" + list).with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Luglio", "discountPercent": 10}
                        """));

        // 8.30 + 8.30 = 16.60, ten per cent off is 1.66, leaving 14.94.
        // Rounded on the subtotal, not per line: discounting each row and
        // adding them up gives a different figure, and the shop discounts
        // the order.
        mvc.perform(get("/api/purchases/" + list).with(user(member)))
                .andExpect(jsonPath("$.subtotalChfCents").value(1660))
                .andExpect(jsonPath("$.discountAppliedCents").value(166))
                .andExpect(jsonPath("$.totalChfCents").value(1494))
                .andExpect(jsonPath("$.totalEurCents").value(1380));
    }

    /** A flat discount larger than the list must not owe the shop money. */
    @Test
    void aFlatDiscountCannotExceedTheTotal() throws Exception {
        long series = anEdition();
        long list = aList("Agosto");
        mvc.perform(addLine(list, series, 1, 690, 500));

        mvc.perform(put("/api/purchases/" + list).with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Agosto", "discountCents": 900}
                        """));

        mvc.perform(get("/api/purchases/" + list).with(user(member)))
                .andExpect(jsonPath("$.totalChfCents").value(0));
    }

    @Test
    void listsBelongToTheirOwner() throws Exception {
        long list = aList("Luglio");

        mvc.perform(get("/api/purchases/" + list).with(user(other)))
                .andExpect(status().isNotFound());
    }

    /** The suggestion carries the prices of the highest volume bought. */
    @Test
    void theNextVolumeIsSuggestedWithTheLastPrices() throws Exception {
        long series = anEdition();
        long may = aList("Maggio");
        String mayBody = json(addLine(may, series, 111, 650, 780), 200);
        markPurchased(may, itemId(mayBody, 0));

        long july = aList("Luglio");
        String julyBody = json(addLine(july, series, 113, 690, 890), 200);
        markPurchased(july, itemId(julyBody, 0));

        long august = aList("Agosto");
        mvc.perform(get("/api/purchases/" + august + "/suggestions").with(user(member)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].volumeNumber").value(114))
                .andExpect(jsonPath("$[0].priceChfCents").value(890));
    }

    /** A planned volume is still missing, so it must remain the next suggestion. */
    @Test
    void anUnboughtVolumeDoesNotAdvanceTheSuggestion() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");

        String boughtBody = json(addLine(july, series, 10, 650, 780), 200);
        markPurchased(july, itemId(boughtBody, 0));
        mvc.perform(addLine(july, series, 11, 690, 890));

        long august = aList("Agosto");
        mvc.perform(get("/api/purchases/" + august + "/suggestions").with(user(member)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].volumeNumber").value(11))
                .andExpect(jsonPath("$[0].priceChfCents").value(780));
    }

    @Test
    void aSuggestionAlreadyOnTheListIsNotRepeated() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");
        String julyBody = json(addLine(july, series, 113, 690, 890), 200);
        markPurchased(july, itemId(julyBody, 0));

        long august = aList("Agosto");
        mvc.perform(addLine(august, series, 114, 690, 890));

        mvc.perform(get("/api/purchases/" + august + "/suggestions").with(user(member)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void movingAListOntoTheShelfIsRepeatable() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");
        String body = json(addLine(list, series, 1, 690, 830), 200);
        long first = itemId(body, 0);
        body = json(addLine(list, series, 2, 690, 830), 200);
        long second = itemId(body, 1);
        markPurchased(list, first);
        markPurchased(list, second);

        mvc.perform(post("/api/purchases/" + list + "/to-collection")
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.alreadyOwned").value(0));

        mvc.perform(post("/api/purchases/" + list + "/to-collection")
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.alreadyOwned").value(2));

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.ownedCount").value(2));
    }

    @Test
    void onlyPurchasedLinesMoveOntoTheShelf() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");
        String body = json(addLine(list, series, 1, 690, 830), 200);
        markPurchased(list, itemId(body, 0));
        mvc.perform(addLine(list, series, 2, 690, 830));

        mvc.perform(post("/api/purchases/" + list + "/to-collection")
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.alreadyOwned").value(0))
                .andExpect(jsonPath("$.notPurchased").value(1));

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.ownedCount").value(1))
                .andExpect(jsonPath("$.ownedNumbers[0]").value(1));
    }

    /** Ordinary members move their own lists: nothing shared is written. */
    @Test
    void movingAListNeedsNoAdministrator() throws Exception {
        long series = anEdition();
        long list = idOf(json(post("/api/purchases").with(user(other)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Luglio"}
                        """), 201));

        String body = json(post("/api/purchases/" + list + "/items")
                .with(user(other)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"seriesId": %d, "volumeNumber": 7}
                        """.formatted(series)), 200);
        markPurchasedAs(list, itemId(body, 0), other);

        mvc.perform(post("/api/purchases/" + list + "/to-collection")
                        .with(user(other)).with(csrf()))
                .andExpect(jsonPath("$.added").value(1));
    }

    /**
     * A list is written ahead of the month, so counting everything on it
     * would report as spent what was only planned.
     */
    @Test
    void statisticsCountOnlyWhatWasBought() throws Exception {
        long series = anEdition();
        long list = aList("Luglio", 2026, 7);

        String body = json(addLine(list, series, 1, 690, 1000), 200);
        long bought = itemId(body, 0);
        mvc.perform(addLine(list, series, 2, 690, 1000));

        mvc.perform(get("/api/purchases/stats").with(user(member)))
                .andExpect(jsonPath("$.netChfCents").value(0));

        mvc.perform(put("/api/purchases/" + list + "/items/" + bought + "/purchased")
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"purchased": true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/purchases/stats").with(user(member)))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].volumeCount").value(1))
                .andExpect(jsonPath("$.netChfCents").value(1000));
    }

    /**
     * Closing a list says the month is over, not that everything on it was
     * taken: what was not bought is carried into the next one, and until it
     * is bought it was not spent.
     */
    @Test
    void closingAListDoesNotCountItsLinesAsBought() throws Exception {
        long series = anEdition();
        long list = aList("Luglio", 2026, 7);
        mvc.perform(addLine(list, series, 1, 690, 1000));

        mvc.perform(put("/api/purchases/" + list + "/paid").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"paid": true}
                        """));

        mvc.perform(get("/api/purchases/stats").with(user(member)))
                .andExpect(jsonPath("$.netChfCents").value(0));
    }

    /** A settled list is a record: the figures stop moving. */
    @Test
    void aClosedListCannotBeEdited() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");
        String body = json(addLine(list, series, 1, 690, 830), 200);
        long item = itemId(body, 0);

        mvc.perform(put("/api/purchases/" + list + "/paid").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"paid": true}
                        """));

        mvc.perform(addLine(list, series, 2, 690, 830))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("list_is_paid"));

        mvc.perform(put("/api/purchases/" + list).with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name": "Rinominata"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/purchases/" + list + "/items/" + item)
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"seriesId": %d, "volumeNumber": 2,
                                 "priceEurCents": 700, "priceChfCents": 900}
                                """.formatted(series)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("list_is_paid"));
    }

    @Test
    void aClosedListMustBeReopenedBeforeDeletion() throws Exception {
        long list = aList("Luglio");
        mvc.perform(put("/api/purchases/" + list + "/paid")
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"paid": true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/purchases/" + list).with(user(member)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("list_is_paid"));

        mvc.perform(put("/api/purchases/" + list + "/paid")
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"paid": false}
                                """))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/purchases/" + list).with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** What is not bought moves on; what is bought stays where it was paid. */
    @Test
    void unboughtLinesAreCarriedToTheNextList() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");
        String body = json(addLine(july, series, 1, 690, 830), 200);
        long bought = itemId(body, 0);
        mvc.perform(addLine(july, series, 2, 690, 830));

        mvc.perform(put("/api/purchases/" + july + "/items/" + bought + "/purchased")
                .with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"purchased": true}
                        """));

        long august = aList("Agosto");
        mvc.perform(post("/api/purchases/" + august + "/carry-over/" + july)
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.moved").value(1));

        mvc.perform(get("/api/purchases/" + july).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].volumeNumber").value(1));

        mvc.perform(get("/api/purchases/" + august).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].volumeNumber").value(2))
                .andExpect(jsonPath("$.items[0].priceChfCents").value(830));
    }

    /**
     * Carrying from a closed list is the usual case, not an error: the month
     * gets settled, then the next list is written and the leftovers follow.
     */
    @Test
    void leftoversCanBeCarriedOutOfAClosedList() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");
        mvc.perform(addLine(july, series, 1, 690, 830));

        mvc.perform(put("/api/purchases/" + july + "/paid").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"paid": true}
                        """));

        long august = aList("Agosto");
        mvc.perform(post("/api/purchases/" + august + "/carry-over/" + july)
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.moved").value(1));

        mvc.perform(get("/api/purchases/" + july).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void carryOverConsolidatesALineAlreadyInTheTarget() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");
        mvc.perform(addLine(july, series, 2, 690, 830));

        long august = aList("Agosto");
        mvc.perform(post("/api/purchases/" + august + "/items")
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"seriesId": %d, "volumeNumber": 2}
                                """.formatted(series)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/purchases/" + august + "/carry-over/" + july)
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.moved").value(1));

        mvc.perform(get("/api/purchases/" + july).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/purchases/" + august).with(user(member)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].volumeNumber").value(2))
                .andExpect(jsonPath("$.items[0].priceChfCents").value(830));
    }

    // ---------------------------------------------------------------- setup

    private long anEdition() throws Exception {
        return createSeries(admin, createManga(admin, "One Piece"), "Normale");
    }

    private long aList(String name) throws Exception {
        return idOf(json(post("/api/purchases").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("{\"name\": \"" + name + "\"}"), 201));
    }

    private long aList(String name, int year, int month) throws Exception {
        return idOf(json(post("/api/purchases").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "%s", "periodYear": %d, "periodMonth": %d}
                        """.formatted(name, year, month)), 201));
    }

    private long itemId(String listBody, int index) {
        return ((Number) com.jayway.jsonpath.JsonPath
                .read(listBody, "$.items[" + index + "].id")).longValue();
    }

    private void markPurchased(long list, long item) throws Exception {
        markPurchasedAs(list, item, member);
    }

    private void markPurchasedAs(long list, long item, UserPrincipal principal) throws Exception {
        mvc.perform(put("/api/purchases/" + list + "/items/" + item + "/purchased")
                        .with(user(principal)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"purchased": true}
                                """))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder addLine(
            long list, long series, int number, int eur, int chf) {
        return post("/api/purchases/" + list + "/items").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"seriesId": %d, "volumeNumber": %d,
                         "priceEurCents": %d, "priceChfCents": %d}
                        """.formatted(series, number, eur, chf));
    }
}
