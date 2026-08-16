package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting a catalogue row cascades into everybody's data.
 *
 * <p>The foreign keys carry {@code ON DELETE CASCADE}, so without these
 * guards an administrator removing an edition would silently strip volumes
 * from other people's shelves and lines from their purchase lists — with no
 * error, and no way to notice.
 */
class CatalogDeleteGuardIT extends IntegrationTest {

    @Test
    void anEditionSomebodyOwnsCannotBeDeleted() throws Exception {
        long manga = createManga(admin, "One Piece");
        long series = createSeries(admin, manga, "Normale");

        mvc.perform(post("/api/collection/series/" + series + "/volumes/1")
                        .with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/series/" + series).with(user(admin)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("series_has_owned_volumes"));
    }

    /** Even when the shelf belongs to somebody other than the caller. */
    @Test
    void theOwnerNeedNotBeTheAdministrator() throws Exception {
        long manga = createManga(admin, "Berserk");
        long series = createSeries(admin, manga, "Maximum");

        mvc.perform(post("/api/collection/series/" + series + "/volumes/1")
                        .with(user(other)).with(csrf()));

        mvc.perform(delete("/api/manga/" + manga).with(user(admin)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("manga_has_owned_volumes"));
    }

    @Test
    void anEditionOnAPurchaseListCannotBeDeleted() throws Exception {
        long manga = createManga(admin, "Detective Conan");
        long series = createSeries(admin, manga, "New Edition");

        long list = idOf(json(post("/api/purchases").with(user(member)).with(csrf())
                .contentType("application/json")
                .content("""
                        {"name": "Luglio"}
                        """), 201));

        mvc.perform(post("/api/purchases/" + list + "/items")
                        .with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"seriesId": %d, "volumeNumber": 72}
                                """.formatted(series)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/series/" + series).with(user(admin)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("series_in_purchase_list"));
    }

    @Test
    void anUntouchedEditionIsDeleted() throws Exception {
        long manga = createManga(admin, "Gachiakuta");
        long series = createSeries(admin, manga, "Normale");

        mvc.perform(delete("/api/series/" + series).with(user(admin)).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
