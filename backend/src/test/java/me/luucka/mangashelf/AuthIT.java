package me.luucka.mangashelf;

import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AuthService;
import me.luucka.mangashelf.user.LoginAttempts;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UserPrincipal;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication through the real HTTP, security, session and database layers. */
class AuthIT extends IntegrationTest {

    private static final String PASSWORD = "a sufficiently long password";

    @Autowired
    private AuthService auth;

    @Autowired
    private LoginAttempts attempts;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void registrationAssignsRolesAndReportsDuplicates() throws Exception {
        users.deleteAll();
        users.flush();

        mvc.perform(register("owner", "owner@example.test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.email").value("owner@example.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(register("reader", "reader@example.test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        mvc.perform(register("OWNER", "another@example.test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("username_taken"));
        mvc.perform(register("another", "READER@EXAMPLE.TEST"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_taken"));
    }

    @Test
    void invalidRegistrationHasFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "?", "email": "not-an-email",
                                 "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void passwordsBeyondTheBcryptLimitAreRejectedCleanly() throws Exception {
        String tooLong = "x".repeat(73);
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "long-password", "email": "long@example.test",
                                 "password": "%s"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password_too_long"));

        try {
            mvc.perform(login("member", tooLong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_credentials"));
        } finally {
            attempts.recordSuccess("member");
        }
    }

    @Test
    void loginRotatesAndPersistsTheSessionUntilLogout() throws Exception {
        mvc.perform(register("session-user", "session@example.test"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        MockHttpSession anonymous = new MockHttpSession();
        String anonymousId = anonymous.getId();
        MvcResult loggedIn = mvc.perform(login("session-user", PASSWORD)
                        .session(anonymous))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("session-user"))
                .andReturn();

        MockHttpSession authenticated =
                (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(anonymousId);

        mvc.perform(get("/api/auth/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("session-user"));
        mvc.perform(post("/api/auth/logout").session(authenticated).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(login("SESSION@EXAMPLE.TEST", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("session-user"));
    }

    @Test
    void loginErrorsDoNotLeakAccountsAndRepeatedFailuresAreBlocked() throws Exception {
        mvc.perform(register("locked", "locked@example.test"))
                .andExpect(status().isCreated());
        mvc.perform(register("disabled", "disabled@example.test"))
                .andExpect(status().isCreated());

        AppUser disabled = users.findByUsernameIgnoreCase("disabled").orElseThrow();
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);

        try {
            mvc.perform(login("does-not-exist", "wrong password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_credentials"));
            mvc.perform(login("disabled", PASSWORD))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("account_disabled"));

            for (int failure = 0; failure < 5; failure++) {
                mvc.perform(login("locked", "wrong password"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("invalid_credentials"));
            }
            mvc.perform(login("locked", PASSWORD))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error").value("too_many_attempts"));
        } finally {
            attempts.recordSuccess("does-not-exist");
            attempts.recordSuccess("disabled");
            attempts.recordSuccess("locked");
        }
    }

    @Test
    void languageIsPersistedAndOnlySupportedValuesAreAccepted() throws Exception {
        mvc.perform(get("/api/auth/me").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("it"));

        mvc.perform(put("/api/auth/me/language").with(user(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language": "en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));

        assertThat(users.findById(member.id()).orElseThrow().getLanguage().code())
                .isEqualTo("en");

        mvc.perform(put("/api/auth/me/language").with(user(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language": "xx"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void registrationKeepsTheChosenLanguage() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "english-user", "email": "english@example.test",
                                 "password": "%s", "language": "en"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("en"));

        assertThat(users.findByUsernameIgnoreCase("english-user").orElseThrow()
                .getLanguage().code()).isEqualTo("en");
    }

    @Test
    void profileUpdateRequiresThePasswordAndKeepsIdentityUnique() throws Exception {
        UserPrincipal signedIn = withPassword(member, PASSWORD);
        AppUser existingAdmin = users.findById(admin.id()).orElseThrow();
        existingAdmin.setEmail("admin@example.test");
        users.saveAndFlush(existingAdmin);

        mvc.perform(put("/api/auth/me/profile").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "reader", "email": "READER@EXAMPLE.TEST",
                                 "currentPassword": "wrong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("current_password_invalid"));

        mvc.perform(put("/api/auth/me/profile").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "email": "reader@example.test",
                                 "currentPassword": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("username_taken"));

        mvc.perform(put("/api/auth/me/profile").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "reader", "email": "ADMIN@EXAMPLE.TEST",
                                 "currentPassword": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_taken"));

        mvc.perform(put("/api/auth/me/profile").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "reader", "email": "READER@EXAMPLE.TEST",
                                 "currentPassword": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reader"))
                .andExpect(jsonPath("$.email").value("reader@example.test"));

        AppUser updated = users.findById(member.id()).orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("reader");
        assertThat(updated.getEmail()).isEqualTo("reader@example.test");
    }

    @Test
    void passwordChangeRejectsWrongAndOverlongSecretsThenRotatesTheHash() throws Exception {
        UserPrincipal signedIn = withPassword(member, PASSWORD);
        String replacement = "a different secure password";

        mvc.perform(put("/api/auth/me/password").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong password",
                                 "newPassword": "%s"}
                                """.formatted(replacement)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("current_password_invalid"));

        mvc.perform(put("/api/auth/me/password").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(PASSWORD, "x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password_too_long"));

        mvc.perform(put("/api/auth/me/password").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(PASSWORD, replacement)))
                .andExpect(status().isNoContent());

        String hash = users.findById(member.id()).orElseThrow().getPasswordHash();
        assertThat(encoder.matches(PASSWORD, hash)).isFalse();
        assertThat(encoder.matches(replacement, hash)).isTrue();
    }

    @Test
    void regularUserCanDeleteTheAccountButAdministratorCannot() throws Exception {
        UserPrincipal signedInMember = withPassword(member, PASSWORD);
        UserPrincipal signedInAdmin = withPassword(admin, PASSWORD);

        mvc.perform(delete("/api/auth/me").with(user(signedInAdmin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin_account_delete_forbidden"));
        assertThat(users.existsById(admin.id())).isTrue();

        MockHttpSession session = new MockHttpSession();
        mvc.perform(delete("/api/auth/me").with(user(signedInMember)).with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(users.existsById(member.id())).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void simultaneousFirstRegistrationsElectExactlyOneAdministrator() throws Exception {
        users.deleteAll();
        users.flush();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AppUser> first = executor.submit(() -> {
                start.await();
                return auth.register(request("first"));
            });
            Future<AppUser> second = executor.submit(() -> {
                start.await();
                return auth.register(request("second"));
            });

            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(users.findAll())
                .extracting(AppUser::getRole)
                .containsExactlyInAnyOrder(Role.ADMIN, Role.USER);
    }

    private RegisterRequest request(String username) {
        return new RegisterRequest(
                username,
                username + "@example.test",
                PASSWORD,
                null);
    }

    private MockHttpServletRequestBuilder register(String username, String email) {
        return post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "email": "%s", "password": "%s"}
                        """.formatted(username, email, PASSWORD));
    }

    private MockHttpServletRequestBuilder login(String login, String password) {
        return post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login": "%s", "password": "%s"}
                        """.formatted(login, password));
    }

    private UserPrincipal withPassword(UserPrincipal principal, String password) {
        AppUser user = users.findById(principal.id()).orElseThrow();
        user.setPasswordHash(encoder.encode(password));
        return UserPrincipal.from(users.saveAndFlush(user));
    }
}
