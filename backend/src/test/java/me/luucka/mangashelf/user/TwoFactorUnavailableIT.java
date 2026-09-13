package me.luucka.mangashelf.user;

import me.luucka.mangashelf.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TwoFactorUnavailableIT extends IntegrationTest {
    @Autowired PasswordEncoder passwords;

    @Test void missingKeyPreventsEnrollmentAndNeverBypassesExistingMfa() throws Exception {
        mvc.perform(get("/api/auth/2fa").with(user(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/auth/2fa/setup").with(user(member)).with(csrf())
                        .contentType("application/json").content("{\"currentPassword\":\"password-123\"}"))
                .andExpect(status().isServiceUnavailable());
        AppUser account = users.findById(member.id()).orElseThrow();
        account.setPasswordHash(passwords.encode("password-123"));
        account.setTwoFactorSecret(new TwoFactorCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .encrypt(member.id(), Totp.newSecret()));
        users.saveAndFlush(account);
        MockHttpSession pending = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json").content("{\"login\":\"member\",\"password\":\"password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andReturn().getRequest().getSession(false);
        mvc.perform(post("/api/auth/2fa/login").session(pending).with(csrf())
                        .contentType("application/json").content("{\"code\":\"123456\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/auth/me").session(pending)).andExpect(status().isUnauthorized());
    }
}
