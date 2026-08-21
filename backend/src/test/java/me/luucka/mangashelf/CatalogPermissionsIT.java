package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue is shared: everyone reads it, only administrators write.
 *
 * <p>Checked through HTTP rather than by calling the service, because the
 * rule lives in the security chain and a service-level test would pass
 * while the endpoint stayed wide open.
 */
class CatalogPermissionsIT extends IntegrationTest {

    @Test
    void anyoneCanReadTheCatalogue() throws Exception {
        mvc.perform(get("/api/manga").with(user(member)))
                .andExpect(status().isOk());
    }

    /** The client needs both the next slice and stable page metadata. */
    @Test
    void catalogueCanBeReadPastTheFirstPage() throws Exception {
        for (int n = 0; n < 26; n++) {
            createManga(admin, "Manga %02d".formatted(n));
        }

        mvc.perform(get("/api/manga?size=24&page=0").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(24))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(26))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/manga?size=24&page=1").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].titleRomaji").value("Manga 24"))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void cataloguePageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/manga?size=1000000").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void signedOutRequestsAreRefused() throws Exception {
        mvc.perform(get("/api/manga"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsCreateWorks() throws Exception {
        mvc.perform(post("/api/manga").with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"titleRomaji": "One Piece"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/manga").with(user(admin)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"titleRomaji": "One Piece"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titleRomaji").value("One Piece"));
    }

    @Test
    void onlyAdminsChangeOrRemoveWorks() throws Exception {
        long mangaId = createManga(admin, "Berserk");

        mvc.perform(put("/api/manga/" + mangaId).with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"titleRomaji": "Berserk rinominato"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/manga/" + mangaId).with(user(member)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCreateEditions() throws Exception {
        long mangaId = createManga(admin, "Berserk");

        mvc.perform(post("/api/manga/" + mangaId + "/series").with(user(member)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"publisher": "Panini", "name": "Maximum"}
                                """))
                .andExpect(status().isForbidden());
    }

    /** A write without the CSRF token is refused whoever sends it. */
    @Test
    void writesWithoutCsrfAreRefused() throws Exception {
        mvc.perform(post("/api/manga").with(user(admin))
                        .contentType("application/json")
                        .content("""
                                {"titleRomaji": "One Piece"}
                                """))
                .andExpect(status().isForbidden());
    }

}
