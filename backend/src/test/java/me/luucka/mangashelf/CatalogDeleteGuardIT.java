package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalogue deletion must preserve everybody's personal data, including
 * writes committed after the service's friendly pre-delete checks.
 */
class CatalogDeleteGuardIT extends IntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aConcurrentShelfInsertCannotBeCascadedAway(boolean deleteManga) throws Exception {
        long manga = createManga(admin, "Concurrent shelf");
        long series = createSeries(admin, manga, "Edition");
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch inserted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var deletion = executor.submit(() -> {
                assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_volume WHERE series_id = ?",
                            Integer.class, series)).isZero();
                    checked.countDown();
                    try {
                        if (!inserted.await(10, TimeUnit.SECONDS)) throw new AssertionError("Insert timed out");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                    if (deleteManga) jdbc.update("DELETE FROM manga WHERE id = ?", manga);
                    else jdbc.update("DELETE FROM series WHERE id = ?", series);
                })).isInstanceOf(DataIntegrityViolationException.class);
            });
            try {
                assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
                mvc.perform(post("/api/collection/series/" + series + "/volumes/1")
                                .with(user(other)).with(csrf()))
                        .andExpect(status().isNoContent());
            } finally {
                inserted.countDown();
            }
            deletion.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_volume WHERE series_id = ?",
                Integer.class, series)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manga WHERE id = ?",
                Integer.class, manga)).isEqualTo(1);
    }

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

        // Defence also holds if the application pre-check has already passed.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM series WHERE id = ?", series))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM manga WHERE id = ?", manga))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_item WHERE series_id = ?",
                Integer.class, series)).isEqualTo(1);
    }

    @Test
    void anUntouchedEditionIsDeleted() throws Exception {
        long manga = createManga(admin, "Gachiakuta");
        long series = createSeries(admin, manga, "Normale");

        mvc.perform(delete("/api/series/" + series).with(user(admin)).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
