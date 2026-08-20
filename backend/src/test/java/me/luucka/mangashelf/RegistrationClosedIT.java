package me.luucka.mangashelf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The deployment switch must close the public registration endpoint itself. */
@SpringBootTest(properties = "app.registration-enabled=false")
class RegistrationClosedIT extends IntegrationTest {

    @Test
    void disabledRegistrationIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "new-user", "email": "new@example.test",
                                 "password": "a sufficiently long password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("registration_closed"));
    }
}
