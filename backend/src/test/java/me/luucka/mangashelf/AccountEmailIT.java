package me.luucka.mangashelf;

import me.luucka.mangashelf.user.AccountEmailService;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.LoginAttempts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.registration-enabled=true", "app.security.registration-attempts-per-hour=10000",
        "app.security.email-attempts-per-hour=10000", "app.email.enabled=true", "app.email.base-url=https://manga.example.test",
        "app.email.from=noreply@example.test"
})
class AccountEmailIT extends IntegrationTest {
    @MockitoBean JavaMailSender sender;
    @Autowired AccountEmailService emails;
    @Autowired LoginAttempts attempts;
    private static final String PASSWORD = "original-password-123";
    private static final String NEW_PASSWORD = "replacement-password-456";

    @BeforeEach
    void prepareMimeMessages() {
        MailTestSupport.prepare(sender);
    }

    @Test
    void healthDoesNotDependOnExternalSmtp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        verifyNoInteractions(sender);
    }

    @Test
    void registrationRequiresConfirmationAndTokenIsSingleUse() throws Exception {
        register();
        AppUser account = account();
        assertThat(account.isEmailVerified()).isFalse();
        String token = lastToken();
        assertThat(account.getVerificationHash()).hasSize(64).doesNotContain(token);
        try {
            login(PASSWORD, 401);
            consume("verify-email", token, null, 204);
            consume("verify-email", token, null, 400);
            login(PASSWORD, 200);
        } finally {
            attempts.recordSuccess("user:" + account.getId());
        }
    }

    @Test
    void resetInvalidatesAllSessionsAndCannotBeReplayed() throws Exception {
        verifiedAccount();
        MockHttpSession session = login(PASSWORD, 200);
        emails.requestEmail("reader@example.test", false);
        String token = lastToken();
        assertThat(account().getResetHash()).hasSize(64).doesNotContain(token);
        consume("reset-password", token, NEW_PASSWORD, 204);
        consume("reset-password", token, "another-password-789", 400);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        try {
            login(PASSWORD, 401);
            login(NEW_PASSWORD, 200);
        } finally {
            attempts.recordSuccess("user:" + account().getId());
        }
    }

    @Test
    void expiredAndSupersededLinksFailAndPurposesCannotBeSwapped() throws Exception {
        register();
        String old = lastToken();
        emails.requestEmail("reader@example.test", true);
        String current = lastToken();
        consume("verify-email", old, null, 400);
        consume("reset-password", current, NEW_PASSWORD, 400);
        AppUser account = account();
        account.setVerificationExpiresAt(Instant.now().minusSeconds(1));
        users.saveAndFlush(account);
        consume("verify-email", current, null, 400);
        emails.requestEmail("reader@example.test", true);
        consume("verify-email", lastToken(), null, 204);
        emails.requestEmail("reader@example.test", false);
        current = lastToken();
        consume("verify-email", current, null, 400);
        account = account();
        account.setResetExpiresAt(Instant.now().minusSeconds(1));
        users.saveAndFlush(account);
        consume("reset-password", current, NEW_PASSWORD, 400);
    }

    @Test
    void disabledAccountsAndChangedCredentialsInvalidateResetLinks() throws Exception {
        verifiedAccount();
        MockHttpSession session = login(PASSWORD, 200);
        emails.requestEmail("reader@example.test", false);
        String old = lastToken();
        mvc.perform(put("/api/auth/me/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        consume("reset-password", old, "other-password-789", 400);
        emails.requestEmail("reader@example.test", false);
        String token = lastToken();
        AppUser account = account();
        account.setEnabled(false);
        users.saveAndFlush(account);
        consume("reset-password", token, "other-password-789", 400);
        clearInvocations(sender);
        emails.requestEmail("reader@example.test", false);
        verifyNoInteractions(sender);
    }

    @Test
    void smtpFailureRollsBackRegistrationAndPreservesPreviouslyDeliveredLink() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        register(503);
        assertThat(users.findByEmailIgnoreCase("reader@example.test")).isEmpty();
        reset(sender);
        MailTestSupport.prepare(sender);
        verifiedAccount();
        emails.requestEmail("reader@example.test", false);
        String token = lastToken();
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> emails.requestEmail("reader@example.test", false))
                .isInstanceOf(MailSendException.class);
        consume("reset-password", token, NEW_PASSWORD, 204);
    }

    @Test
    void requestEndpointsAreGenericAndRequireCsrf() throws Exception {
        for (String endpoint : new String[]{"forgot-password", "resend-verification"}) {
            mvc.perform(post("/api/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"missing@example.test\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/" + endpoint).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"missing@example.test\"}"))
                    .andExpect(status().isAccepted()).andExpect(content().string(""));
        }
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + "a".repeat(43) + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentConsumptionAllowsOnlyOnePasswordReset() throws Exception {
        verifiedAccount();
        emails.requestEmail("reader@example.test", false);
        String token = lastToken();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> consumeStatus(token));
            var second = executor.submit(() -> consumeStatus(token));
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 400);
        }
    }

    @Test
    void invalidPasswordsDoNotConsumeResetToken() throws Exception {
        verifiedAccount();
        emails.requestEmail("reader@example.test", false);
        String token = lastToken();
        consume("reset-password", token, "short", 400);
        consume("reset-password", token, "é".repeat(37), 400);
        consume("reset-password", token, PASSWORD, 400);
        consume("reset-password", token, NEW_PASSWORD, 204);
    }

    private int consumeStatus(String token) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private void register() throws Exception { register(201); }

    private void register(int status) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader\",\"email\":\"reader@example.test\",\"password\":\"" + PASSWORD + "\",\"language\":\"en\"}"))
                .andExpect(status().is(status));
    }

    private void verifiedAccount() throws Exception {
        register();
        consume("verify-email", lastToken(), null, 204);
    }

    private AppUser account() { return users.findByEmailIgnoreCase("reader@example.test").orElseThrow(); }

    private String lastToken() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, atLeastOnce()).send(captor.capture());
        String body = MailTestSupport.body(MailTestSupport.delivered(captor.getValue()), "text/plain");
        assertThat(body).contains("https://manga.example.test/").contains("#token=");
        return body.split("#token=")[1].split("\\s")[0];
    }

    private MockHttpSession login(String password, int status) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"reader\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(status)).andReturn().getRequest().getSession(false);
    }

    private void consume(String endpoint, String token, String password, int expected) throws Exception {
        mvc.perform(post("/api/auth/" + endpoint).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"" + (password == null ? "" : ",\"newPassword\":\"" + password + "\"") + "}"))
                .andExpect(status().is(expected));
    }
}
