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
