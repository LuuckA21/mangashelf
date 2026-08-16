package me.luucka.mangashelf;

import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AppUserRepository;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UserPrincipal;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for the integration tests: a real Postgres, the real security chain,
 * and requests that go through the actual HTTP layer.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}. A test
 * transaction would stay open while the controller builds its response, and
 * every lazily-loaded association would resolve happily — hiding exactly the
 * class of bug that has bitten this project three times. Without it the
 * session closes where it closes in production, so a DTO reaching for an
 * unfetched association fails here too.
 *
 * <p>The price is cleaning up by hand between tests, which
 * {@link #resetDatabase()} does.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    /**
     * One container for the whole run, started on first class load and left
     * to Ryuk to remove. Starting one per class would add half a minute per
     * file for no isolation the truncation below does not already give.
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    protected UserPrincipal admin;
    protected UserPrincipal member;
    protected UserPrincipal other;

    @BeforeEach
    void resetDatabase() {
        // Truncate rather than delete: it resets the sequences too, so ids
        // start from 1 in every test and a failure reads the same way twice.
        jdbc.execute("""
                TRUNCATE purchase_item, purchase_list, user_volume,
                         series, manga, app_user
                RESTART IDENTITY CASCADE
                """);

        admin = save("admin", Role.ADMIN);
        member = save("member", Role.USER);
        other = save("other", Role.USER);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Performs a request and returns the body, failing the test when the
     * status is not the expected one — so a helper that silently returned an
     * error page cannot make a later assertion fail somewhere confusing.
     */
    protected String json(MockHttpServletRequestBuilder request, int expectedStatus)
            throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    protected long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected long createManga(UserPrincipal as, String title) throws Exception {
        return idOf(json(post("/api/manga").with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"titleRomaji": "%s"}
                        """.formatted(title)), 201));
    }

    protected long createSeries(UserPrincipal as, long mangaId, String name) throws Exception {
        return createSeries(as, mangaId, name, null, false);
    }

    protected long createSeries(UserPrincipal as, long mangaId, String name,
                                Integer totalVolumes, boolean completed) throws Exception {
        return idOf(json(post("/api/manga/" + mangaId + "/series")
                .with(user(as)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publisher": "Star Comics", "name": "%s",
                         "language": "it", "totalVolumes": %s, "completed": %s}
                        """.formatted(name, totalVolumes, completed)), 201));
    }

    private UserPrincipal save(String username, Role role) {
        AppUser user = new AppUser(username, username + "@localhost", "x".repeat(60));
        user.setRole(role);
        return UserPrincipal.from(users.save(user));
    }
}
