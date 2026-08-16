package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
        mvc.perform(addLine(may, series, 111, 650, 780));

        long july = aList("Luglio");
        mvc.perform(addLine(july, series, 113, 690, 890));

        long august = aList("Agosto");
        mvc.perform(get("/api/purchases/" + august + "/suggestions").with(user(member)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].volumeNumber").value(114))
                .andExpect(jsonPath("$[0].priceChfCents").value(890));
    }

    @Test
    void aSuggestionAlreadyOnTheListIsNotRepeated() throws Exception {
        long series = anEdition();
        long july = aList("Luglio");
        mvc.perform(addLine(july, series, 113, 690, 890));

        long august = aList("Agosto");
        mvc.perform(addLine(august, series, 114, 690, 890));

        mvc.perform(get("/api/purchases/" + august + "/suggestions").with(user(member)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void movingAListOntoTheShelfIsRepeatable() throws Exception {
        long series = anEdition();
        long list = aList("Luglio");
        mvc.perform(addLine(list, series, 1, 690, 830));
        mvc.perform(addLine(list, series, 2, 690, 830));

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

    /** Ordinary members move their own lists: nothing shared is written. */
    @Test
    void movingAListNeedsNoAdministrator() throws Exception {
        long series = anEdition();
        long list = idOf(json(post("/api/purchases").with(user(other)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Luglio"}
                        """), 201));

        mvc.perform(post("/api/purchases/" + list + "/items").with(user(other)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"seriesId": %d, "volumeNumber": 7}
                        """.formatted(series)));

        mvc.perform(post("/api/purchases/" + list + "/to-collection")
                        .with(user(other)).with(csrf()))
                .andExpect(jsonPath("$.added").value(1));
    }

    @Test
    void statisticsGroupByYear() throws Exception {
        long series = anEdition();

        long list = idOf(json(post("/api/purchases").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Luglio", "periodYear": 2026, "periodMonth": 7,
                         "discountPercent": 10}
                        """), 201));
        mvc.perform(addLine(list, series, 1, 690, 1000));

        mvc.perform(get("/api/purchases/stats").with(user(member)))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].fullChfCents").value(1000))
                .andExpect(jsonPath("$.years[0].netChfCents").value(900))
                .andExpect(jsonPath("$.netChfCents").value(900));
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

    private org.springframework.test.web.servlet.RequestBuilder addLine(
            long list, long series, int number, int eur, int chf) {
        return post("/api/purchases/" + list + "/items").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"seriesId": %d, "volumeNumber": %d,
                         "priceEurCents": %d, "priceChfCents": %d}
                        """.formatted(series, number, eur, chf));
    }
}
