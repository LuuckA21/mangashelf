package me.luucka.mangashelf.user;

import com.jayway.jsonpath.JsonPath;
import me.luucka.mangashelf.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "app.security.two-factor-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class TwoFactorIT extends IntegrationTest {
    private static final String PASSWORD = "two-factor-password-123";
    @Autowired TwoFactorService factors;
    @Autowired TwoFactorCipher cipher;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountEmailService emails;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccountMailer mailer;

    @BeforeEach void credentials() {
        AppUser account = users.findById(member.id()).orElseThrow();
        account.setPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(account);
        // Reset test-instance limits because the base fixture reuses user IDs.
        ReflectionTestUtils.setField(factors, "attempts", new LoginAttempts());
        when(mailer.enabled()).thenReturn(true);
    }

    @Test void enrollmentRequiresPasswordAndProofAndStoresNoPlaintextSecrets() throws Exception {
        mvc.perform(post("/api/auth/2fa/setup").with(user(member)).with(csrf())
                        .contentType("application/json").content("{\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        assertThat(account().getTwoFactorPendingSecret()).isNull();
        var setup = setup();
        assertThat(account().getTwoFactorSecret()).isNull();
        assertThat(account().getTwoFactorPendingSecret()).doesNotContain(setup);
        mvc.perform(post("/api/auth/2fa/enable").with(user(member)).with(csrf())
                        .contentType("application/json").content("{\"code\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        assertThat(account().getTwoFactorSecret()).isNull();
        List<String> codes = confirmSetup();
        verify(mailer, timeout(2000)).sendSecurityNotice(eq("member@localhost"), eq(UiLanguage.IT), eq(TwoFactorNotifications.Action.ENABLED));
        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(account().getTwoFactorPendingSecret()).isNull();
        assertThat(account().getTwoFactorSecret()).doesNotContain(setup);
        List<String> hashes = jdbc.queryForList("SELECT code_hash FROM two_factor_recovery_code", String.class);
        assertThat(hashes).hasSize(10).allMatch(value -> value.matches("[0-9a-f]{64}"));
        for (String code : codes) assertThat(hashes).doesNotContain(code.replace("-", ""));
        mvc.perform(get("/api/auth/me").with(user(member))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(user(current())))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.twoFactorSecret").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void passwordAloneGivesNoProtectedAccessAndFullLoginRotatesTheSession() throws Exception {
        List<String> codes = enable();
        MockHttpSession pending = challenge(PASSWORD);
        String before = pending.getId();
        assertThat(pending.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        var state = (LoginSession.Pending) pending.getAttribute(LoginSession.PENDING);
        assertThat(state.principal.getPassword()).isNull();
        assertThat(pending.getMaxInactiveInterval()).isEqualTo(300);
        for (String path : List.of("/api/auth/me", "/api/manga", "/api/collection/volumes", "/api/admin/users", "/api/auth/2fa")) {
            mvc.perform(get(path).session(pending)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/2fa/login").session(pending).with(csrf())
                        .contentType("application/json").content(codeBody(codes.getFirst())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorEnabled").value(true));
        assertThat(pending.getId()).isNotEqualTo(before);
        assertThat(pending.getAttribute(LoginSession.PENDING)).isNull();
        var context = (SecurityContext) pending.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(((UserPrincipal) context.getAuthentication().getPrincipal()).password()).isNull();
        mvc.perform(get("/api/auth/me").session(pending)).andExpect(status().isOk());
        assertThat(finish(pending, codes.get(1))).isEqualTo(401); // challenge consumed
    }

    @Test void wrongPasswordDoesNotDiscloseMfaStateOrCreateAChallenge() throws Exception {
        enable();
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"login\":\"member\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.twoFactorRequired").doesNotExist()).andReturn();
        var session = result.getRequest().getSession(false);
        assertThat(session == null || session.getAttribute(LoginSession.PENDING) == null).isTrue();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void concurrentLoginsCannotReuseTotpOrRecoveryCodes(boolean recovery) throws Exception {
        List<String> codes = enable();
        MockHttpSession first = challenge(PASSWORD);
        MockHttpSession second = challenge(PASSWORD);
        String code = recovery ? codes.getFirst() : Totp.code(cipher.decrypt(member.id(), account().getTwoFactorSecret()),
                account().getTwoFactorLastStep() + 1, 6);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return finish(first, code); });
            var b = executor.submit(() -> { start.await(); return finish(second, code); });
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(factors.status(current()).recoveryCodesRemaining()).isEqualTo(recovery ? 9 : 10);
    }

    @Test void newPasswordChallengesDoNotResetSecondFactorFailures() throws Exception {
        List<String> codes = enable();
        for (int i = 0; i < 5; i++) assertThat(finish(challenge(PASSWORD), "invalid-code")).isEqualTo(400);
        assertThat(finish(challenge(PASSWORD), codes.getFirst())).isEqualTo(429);
        assertThat(factors.status(current()).recoveryCodesRemaining()).isEqualTo(10);
    }

    @Test void aChallengeHasAnAbsoluteExpiryAndCanBeCancelled() throws Exception {
        List<String> codes = enable();
        MockHttpSession pending = challenge(PASSWORD);
        ReflectionTestUtils.setField(pending.getAttribute(LoginSession.PENDING), "expires", Instant.now().minusSeconds(1));
        assertThat(finish(pending, codes.getFirst())).isEqualTo(401);
        MockHttpSession cancel = challenge(PASSWORD);
        mvc.perform(post("/api/auth/2fa/cancel").session(cancel).with(csrf())).andExpect(status().isNoContent());
        assertThat(cancel.isInvalid()).isTrue();
        mvc.perform(post("/api/auth/2fa/login").with(csrf()).contentType("application/json").content(codeBody(codes.getFirst())))
                .andExpect(status().isUnauthorized());
        assertThat(factors.status(current()).recoveryCodesRemaining()).isEqualTo(10);
    }

    @Test void expiredOrCancelledSetupCannotBeEnabled() throws Exception {
        setup();
        AppUser account = account();
        account.setTwoFactorPendingExpiresAt(Instant.now().minusSeconds(1));
        users.saveAndFlush(account);
        mvc.perform(post("/api/auth/2fa/enable").with(user(member)).with(csrf())
                        .contentType("application/json").content(codeBody("123456")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("two_factor_setup_expired"));
        setup();
        mvc.perform(delete("/api/auth/2fa/setup").with(user(member)).with(csrf())).andExpect(status().isNoContent());
        assertThat(account().getTwoFactorPendingSecret()).isNull();
    }

    @Test void passwordResetKeepsMfaAndRevokesPendingChallenges() throws Exception {
        List<String> codes = enable();
        MockHttpSession pending = challenge(PASSWORD);
        String token = "A".repeat(43);
        AppUser account = account();
        account.setResetHash(AccountEmailService.hash(token));
        account.setResetExpiresAt(Instant.now().plusSeconds(60));
        account.setResetSessionVersion(account.getSessionVersion());
        users.saveAndFlush(account);
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"new-password-456\"}"))
                .andExpect(status().isNoContent());
        assertThat(account().getTwoFactorSecret()).isNotNull();
        assertThat(finish(pending, codes.getFirst())).isEqualTo(401);
        assertThat(finish(challenge("new-password-456"), codes.getFirst())).isEqualTo(200);
    }

    @Test void rotatingCodesAndDisablingRequireBothFactorsAndInvalidateOldCredentials() throws Exception {
        List<String> old = enable();
        MockHttpSession pending = challenge(PASSWORD);
        mvc.perform(post("/api/auth/2fa/disable").with(user(current())).with(csrf()).contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(account().getTwoFactorSecret()).isNotNull();
        mvc.perform(post("/api/auth/2fa/disable").with(user(current())).with(csrf()).contentType("application/json")
                        .content(credentials("wrong", old.getFirst())))
                .andExpect(status().isBadRequest());
        String response = json(post("/api/auth/2fa/recovery-codes").with(user(current())).with(csrf())
                .contentType("application/json").content(credentials(PASSWORD, old.getFirst())), 200);
        List<String> next = JsonPath.read(response, "$.recoveryCodes");
        assertThat(next).hasSize(10).doesNotContainAnyElementsOf(old);
        assertThat(finish(pending, next.getFirst())).isEqualTo(401);
        assertThat(finish(challenge(PASSWORD), old.get(1))).isEqualTo(400);
        mvc.perform(post("/api/auth/2fa/disable").with(user(current())).with(csrf()).contentType("application/json")
                        .content(credentials(PASSWORD, next.getFirst())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorEnabled").value(false));
        assertThat(factors.status(current()).recoveryCodesRemaining()).isZero();
        assertThat(account().getTwoFactorSecret()).isNull();
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"login\":\"member\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.id()));
    }

    @Test void passwordChangesAndDeletionRequestsRequireAFreshFactor() throws Exception {
        List<String> codes = enable();
        mvc.perform(put("/api/auth/me/password").with(user(current())).with(csrf()).contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-password-456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("two_factor_invalid"));
        mvc.perform(post("/api/auth/me/deletion-request").with(user(current())).with(csrf()).contentType("application/json")
                        .content(credentials(PASSWORD, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("two_factor_invalid"));
        verify(mailer, never()).sendDeletion(any(), any());
        mvc.perform(post("/api/auth/me/deletion-request").with(user(current())).with(csrf()).contentType("application/json")
                        .content(credentials(PASSWORD, codes.getFirst())))
                .andExpect(status().isAccepted());
        verify(mailer).sendDeletion(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"/setup", "/enable", "/disable", "/recovery-codes", "/login", "/cancel"})
    void allMutationEndpointsRequireCsrf(String path) throws Exception {
        mvc.perform(post("/api/auth/2fa" + path).with(user(member)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    private AppUser account() { return users.findById(member.id()).orElseThrow(); }
    private UserPrincipal current() { return UserPrincipal.from(account()).withoutCredentials(); }
    private String setup() throws Exception {
        return JsonPath.read(json(post("/api/auth/2fa/setup").with(user(current())).with(csrf())
                .contentType("application/json").content(credentials(PASSWORD, null)), 200), "$.secret");
    }
    private List<String> enable() throws Exception { setup(); return confirmSetup(); }
    private List<String> confirmSetup() throws Exception {
        String code = Totp.code(cipher.decrypt(member.id(), account().getTwoFactorPendingSecret()), Instant.now().getEpochSecond() / 30, 6);
        return JsonPath.read(json(post("/api/auth/2fa/enable").with(user(current())).with(csrf())
                .contentType("application/json").content(codeBody(code)), 200), "$.recoveryCodes");
    }
    private MockHttpSession challenge(String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"login\":\"member\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.id").doesNotExist()).andReturn().getRequest().getSession(false);
    }
    private int finish(MockHttpSession session, String code) throws Exception {
        return mvc.perform(post("/api/auth/2fa/login").session(session).with(csrf()).contentType("application/json")
                .content(codeBody(code))).andReturn().getResponse().getStatus();
    }
    private static String codeBody(String code) { return "{\"code\":\"" + code + "\"}"; }
    private static String credentials(String password, String code) {
        return "{\"currentPassword\":\"" + password + "\",\"code\":" + (code == null ? "null" : "\"" + code + "\"") + "}";
    }
}
