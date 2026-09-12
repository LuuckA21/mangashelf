package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MetadataErrorResponseTest {
    @Test
    void cooldownIsExposedAsSafeJsonAndRetryAfterHeader() throws Exception {
        MetadataService service = mock(MetadataService.class);
        when(service.search("Berserk", 10)).thenThrow(new AniListException("anilist_rate_limited", 12));
        MockMvcBuilders.standaloneSetup(new MetadataController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/api/metadata/search").param("q", "Berserk"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.error").value("anilist_rate_limited"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(12));
    }
}
