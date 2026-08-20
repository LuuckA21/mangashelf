package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Marking volumes as owned, and reading back what is missing. */
class CollectionIT extends IntegrationTest {

    @Test
    void aMemberMarksAndUnmarksTheirOwnVolumes() throws Exception {
        long series = anEdition();

        mvc.perform(post(url(series, 1)).with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.ownedCount").value(1))
                .andExpect(jsonPath("$.ownedNumbers[0]").value(1));

        mvc.perform(delete(url(series, 1)).with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.ownedCount").value(0));
    }

    /** Volume 0 is optional, but once owned it must remain visible and count. */
    @Test
    void volumeZeroIsRepresentedOnItsOwn() throws Exception {
        long series = anEdition();

        mvc.perform(post(url(series, 0)).with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.upTo").value(0))
                .andExpect(jsonPath("$.progressTotal").value(1))
                .andExpect(jsonPath("$.ownedCount").value(1))
                .andExpect(jsonPath("$.ownedNumbers[0]").value(0))
                .andExpect(jsonPath("$.missingNumbers.length()").value(0));

        mvc.perform(get("/api/collection/summary").with(user(member)))
                .andExpect(jsonPath("$[0].progressTotal").value(1))
                .andExpect(jsonPath("$[0].ownedNumbers[0]").value(0));
    }

    /** One person's shelf is invisible to another's. */
    @Test
    void shelvesAreSeparate() throws Exception {
        long series = anEdition();

        mvc.perform(post(url(series, 1)).with(user(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/collection/series/" + series).with(user(other)))
                .andExpect(jsonPath("$.ownedCount").value(0));
    }

    /**
     * The gap is worked out from the owned numbers alone — there is no
     * catalogue of volumes to compare against, and none is needed.
     */
    @Test
    void gapsInsideARunAreFound() throws Exception {
        long series = anEdition();
        markRange(series, 1, 3);
        mvc.perform(post(url(series, 5)).with(user(member)).with(csrf()));

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.upTo").value(5))
                .andExpect(jsonPath("$.ownedCount").value(4))
                .andExpect(jsonPath("$.missingNumbers.length()").value(1))
                .andExpect(jsonPath("$.missingNumbers[0]").value(4));
    }

    /** With no declared total the shelf stops at the highest owned volume. */
    @Test
    void nothingIsMissingPastTheLastOwnedVolume() throws Exception {
        long series = anEdition();
        markRange(series, 1, 10);

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.upTo").value(10))
                .andExpect(jsonPath("$.missingNumbers.length()").value(0));
    }

    /** A declared total tells the shelf how far the run actually goes. */
    @Test
    void aDeclaredTotalRevealsWhatIsStillToCome() throws Exception {
        long manga = createManga(admin, "Slam Dunk");
        long series = createSeries(admin, manga, "Normale", 12, true);
        markRange(series, 1, 10);

        mvc.perform(get("/api/collection/series/" + series).with(user(member)))
                .andExpect(jsonPath("$.upTo").value(12))
                .andExpect(jsonPath("$.missingNumbers.length()").value(2));
    }

    @Test
    void markingARangeSkipsWhatIsAlreadyOwned() throws Exception {
        long series = anEdition();
        markRange(series, 1, 5);

        mvc.perform(post("/api/collection/series/" + series + "/range?from=1&to=8")
                        .with(user(member)).with(csrf()))
                .andExpect(jsonPath("$.added").value(3));
    }

    /**
     * The upper bound matters more than it looks: a short counter reaching
     * 32767 overflows on the next increment and the loop never ends.
     */
    @Test
    void outlandishRangesAreRefused() throws Exception {
        long series = anEdition();

        mvc.perform(post("/api/collection/series/" + series + "/range?from=1&to=32767")
                        .with(user(member)).with(csrf()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/collection/series/" + series + "/range?from=5&to=1")
                        .with(user(member)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    /** Marking volume 30000 would make every later read build 30000 gaps. */
    @Test
    void outlandishVolumeNumbersAreRefused() throws Exception {
        long series = anEdition();

        mvc.perform(post(url(series, 30000)).with(user(member)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSummaryGroupsByEdition() throws Exception {
        long manga = createManga(admin, "One Piece");
        long normale = createSeries(admin, manga, "Normale");
        long gazzetta = createSeries(admin, manga, "Gazzetta");

        markRange(normale, 1, 3);
        markRange(gazzetta, 1, 2);

        mvc.perform(get("/api/collection/summary").with(user(member)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    private long anEdition() throws Exception {
        return createSeries(admin, createManga(admin, "One Piece"), "Normale");
    }

    private String url(long series, int number) {
        return "/api/collection/series/" + series + "/volumes/" + number;
    }

    private void markRange(long series, int from, int to) throws Exception {
        mvc.perform(post("/api/collection/series/" + series
                        + "/range?from=" + from + "&to=" + to)
                        .with(user(member)).with(csrf()))
                .andExpect(status().isOk());
    }
}
