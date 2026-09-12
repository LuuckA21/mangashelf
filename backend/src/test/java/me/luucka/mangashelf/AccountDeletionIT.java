package me.luucka.mangashelf;

import jakarta.mail.internet.MimeMessage;
import me.luucka.mangashelf.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.registration-enabled=true", "app.security.registration-attempts-per-hour=10000",
        "app.security.email-attempts-per-hour=10000", "app.email.enabled=true",
        "app.email.base-url=https://manga.example.test", "app.email.from=noreply@example.test"
})
class AccountDeletionIT extends IntegrationTest {
    private static final String PASSWORD = "a-current-password-123";
    @MockitoBean JavaMailSender sender;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountEmailService emails;
    @Autowired AuthService auth;

    @BeforeEach
    void prepareMailAndPassword() {
        MailTestSupport.prepare(sender);
        AppUser account = account(member.id());
        account.setPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(account);
    }

    @Test
    void requestRequiresAuthenticationCsrfAndCurrentPassword() throws Exception {
        mvc.perform(request(PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/me/deletion-request").with(user(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(request("wrong-password").with(user(member))).andExpect(status().isBadRequest());
        mvc.perform(request("é".repeat(37)).with(user(member))).andExpect(status().isBadRequest());
        verifyNoInteractions(sender);
        mvc.perform(request(PASSWORD).with(user(member))).andExpect(status().isAccepted());
        assertThat(users.existsById(member.id())).isTrue();
        assertThat(account(member.id()).getDeletionHash()).hasSize(64).doesNotContain(lastToken());
        mvc.perform(request(PASSWORD).with(user(member))).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("deletion_cooldown"));
    }

    @Test
    void openingLinkOnlyPreviewsAndExplicitConfirmationIsRequired() throws Exception {
        String token = issue(member);
        mvc.perform(details(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"))
                .andExpect(jsonPath("$.email").value("member@localhost"));
        mvc.perform(get("/api/auth/delete-account")).andExpect(status().isMethodNotAllowed());
        for (String suffix : new String[]{"", ",\"confirmed\":false", ",\"confirmed\":null"}) {
            mvc.perform(post("/api/auth/delete-account").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"" + suffix + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/delete-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        assertThat(users.existsById(member.id())).isTrue();
        mvc.perform(confirm(token)).andExpect(status().isNoContent());
        mvc.perform(confirm(token)).andExpect(status().isBadRequest());
    }

    @Test
    void removesOnlyPersonalDataAnonymizesAuditAndInvalidatesSessions() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"member\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        long manga = createManga(admin, "Shared catalogue");
        long series = createSeries(admin, manga, "Edition");
        for (long id : new long[]{member.id(), other.id()}) {
            jdbc.update("INSERT INTO user_volume (user_id, series_id, number) VALUES (?, ?, 1)", id, series);
            long list = jdbc.queryForObject("INSERT INTO purchase_list (user_id, name) VALUES (?, 'List') RETURNING id", Long.class, id);
            jdbc.update("INSERT INTO purchase_item (list_id, series_id, volume_number) VALUES (?, ?, 2)", list, series);
        }
        jdbc.update("INSERT INTO admin_audit_event (actor_user_id,actor_username,target_user_id,target_username,action,old_value,new_value) VALUES (?, 'member', ?, 'other', 'ROLE_CHANGED', 'USER', 'ADMIN')", member.id(), other.id());
        jdbc.update("INSERT INTO admin_audit_event (actor_user_id,actor_username,target_user_id,target_username,action,old_value,new_value) VALUES (?, 'admin', ?, 'member', 'STATUS_CHANGED', 'true', 'false')", admin.id(), member.id());
        String token = issue(member);
        mvc.perform(confirm(token)).andExpect(status().isNoContent());
        assertThat(users.existsById(member.id())).isFalse();
        assertThat(users.existsById(other.id())).isTrue();
        for (String table : new String[]{"manga", "series", "user_volume", "purchase_list", "purchase_item"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_volume WHERE user_id = ?", Long.class, other.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor_username FROM admin_audit_event WHERE actor_user_id IS NULL", String.class)).isEqualTo("[deleted]");
        assertThat(jdbc.queryForObject("SELECT target_username FROM admin_audit_event WHERE target_user_id IS NULL", String.class)).isEqualTo("[deleted]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_event", Long.class)).isEqualTo(2);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void blocksDeletionFromADifferentLoggedInAccount() throws Exception {
        String token = issue(member);
        mvc.perform(details(token).with(user(other))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("deletion_wrong_account"));
        mvc.perform(confirm(token).with(user(other))).andExpect(status().isForbidden());
        assertThat(users.existsById(member.id())).isTrue();
        mvc.perform(confirm(token).with(user(member))).andExpect(status().isNoContent());
    }

    @Test
    void rejectsExpiredSupersededAndWrongPurposeTokens() throws Exception {
        String first = issue(member);
        AppUser account = account(member.id());
        account.setDeletionExpiresAt(Instant.now().minusSeconds(1));
        account.setDeletionRequestedAt(Instant.now().minusSeconds(61));
        users.saveAndFlush(account);
        mvc.perform(confirm(first)).andExpect(status().isBadRequest());
        String second = issue(member);
        mvc.perform(confirm(first)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + second + "\",\"newPassword\":\"a-different-password-456\"}"))
                .andExpect(status().isBadRequest());
        emails.requestEmail("member@localhost", false);
        mvc.perform(confirm(lastToken())).andExpect(status().isBadRequest());
        mvc.perform(confirm(second)).andExpect(status().isNoContent());
    }

    @Test
    void changingPasswordOrAccountStateInvalidatesDeletionLinks() throws Exception {
        String token = issue(member);
        auth.updatePassword(member.id(), PASSWORD, "a-new-password-456");
        mvc.perform(details(token)).andExpect(status().isBadRequest());
        mvc.perform(confirm(token)).andExpect(status().isBadRequest());
        mvc.perform(request(PASSWORD).with(user(member))).andExpect(status().isUnauthorized());
        AppUser account = account(member.id());
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setDeletionRequestedAt(Instant.now().minusSeconds(61));
        users.saveAndFlush(account);
        token = issue(UserPrincipal.from(account));
        mvc.perform(put("/api/admin/users/" + member.id()).with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\",\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(confirm(token)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + member.id()).with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(confirm(token)).andExpect(status().isBadRequest());
        assertThat(users.existsById(member.id())).isTrue();
    }

    @Test
    void failedDeliveryPreservesThePreviousLinkAndCooldown() throws Exception {
        String token = issue(member);
        AppUser account = account(member.id());
        account.setDeletionRequestedAt(Instant.now().minusSeconds(61));
        users.saveAndFlush(account);
        String oldHash = account.getDeletionHash();
        doThrow(new MailSendException("Unavailable")).when(sender).send(any(MimeMessage.class));
        mvc.perform(request(PASSWORD).with(user(member))).andExpect(status().isServiceUnavailable());
        assertThat(account(member.id()).getDeletionHash()).isEqualTo(oldHash);
        assertThat(account(member.id()).getDeletionRequestedAt()).isBefore(Instant.now().minusSeconds(60));
        mvc.perform(confirm(token)).andExpect(status().isNoContent());
    }

    @Test
    void blocksLastAdministratorEvenIfAnotherAdminIsUnverified() throws Exception {
        AppUser owner = account(admin.id());
        owner.setPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(owner);
        AppUser pending = account(other.id());
        pending.setRole(Role.ADMIN);
        pending.setEmailVerified(false);
        users.saveAndFlush(pending);
        mvc.perform(request(PASSWORD).with(user(admin))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("last_admin_required"));
        verifyNoInteractions(sender);
    }

    @Test
    void rechecksLastAdministratorAtConfirmationTime() throws Exception {
        AppUser secondAdmin = account(member.id());
        secondAdmin.setRole(Role.ADMIN);
        users.saveAndFlush(secondAdmin);
        UserPrincipal principal = UserPrincipal.from(secondAdmin);
        String token = issue(principal);
        mvc.perform(put("/api/admin/users/" + admin.id()).with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(confirm(token)).andExpect(status().isConflict());
        assertThat(users.existsById(member.id())).isTrue();
    }

    @Test
    void simultaneousAdministratorDeletionsLeaveOneActiveAdministrator() throws Exception {
        AppUser owner = account(admin.id());
        owner.setPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(owner);
        AppUser second = account(member.id());
        second.setRole(Role.ADMIN);
        users.saveAndFlush(second);
        String first = issue(UserPrincipal.from(owner));
        String secondToken = issue(UserPrincipal.from(second));
        assertThat(concurrent(first, secondToken)).containsExactlyInAnyOrder(204, 409);
        assertThat(users.countByRoleAndEnabledTrueAndEmailVerifiedTrue(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void simultaneousConsumptionDeletesOnlyOnce() throws Exception {
        String token = issue(member);
        assertThat(concurrent(token, token)).containsExactlyInAnyOrder(204, 400);
    }

    private List<Integer> concurrent(String first, String second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return mvc.perform(confirm(first)).andReturn().getResponse().getStatus(); });
            var b = executor.submit(() -> { start.await(); return mvc.perform(confirm(second)).andReturn().getResponse().getStatus(); });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private AppUser account(Long id) { return users.findById(id).orElseThrow(); }

    private String issue(UserPrincipal principal) throws Exception {
        mvc.perform(request(PASSWORD).with(user(principal))).andExpect(status().isAccepted());
        return lastToken();
    }

    private String lastToken() throws Exception {
        var capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, atLeastOnce()).send(capture.capture());
        String body = MailTestSupport.body(MailTestSupport.delivered(capture.getValue()), "text/plain");
        return body.split("#token=")[1].split("\\s")[0];
    }

    private MockHttpServletRequestBuilder request(String password) {
        return post("/api/auth/me/deletion-request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + password + "\"}");
    }

    private MockHttpServletRequestBuilder details(String token) {
        return post("/api/auth/deletion-details").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private MockHttpServletRequestBuilder confirm(String token) {
        return post("/api/auth/delete-account").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"confirmed\":true}");
    }
}
