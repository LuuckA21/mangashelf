package me.luucka.mangashelf;

import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.AdminUserService;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UserPrincipal;
import me.luucka.mangashelf.user.dto.AdminUserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountManagementIT extends IntegrationTest {

    @Autowired
    private AdminUserService accounts;

    @Test
    void onlyAdministratorsCanListAndManageAccounts() throws Exception {
        mvc.perform(get("/api/admin/users").with(user(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin_required"));

        mvc.perform(get("/api/admin/users").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].email").value("admin@localhost"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void disableAndRoleChangesExpireExistingAuthorisation() throws Exception {
        mvc.perform(put("/api/admin/users/" + member.id())
                        .with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "USER", "enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/auth/me").with(user(member)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("session_invalid"));

        mvc.perform(put("/api/admin/users/" + member.id())
                        .with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN", "enabled": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true));

        UserPrincipal promoted = UserPrincipal.from(users.findById(member.id()).orElseThrow());
        mvc.perform(get("/api/admin/users").with(user(promoted)))
                .andExpect(status().isOk());
    }

    @Test
    void currentAdministratorCannotChangeTheirOwnAccess() throws Exception {
        mvc.perform(put("/api/admin/users/" + admin.id())
                        .with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "USER", "enabled": true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cannot_modify_self"));
    }

    @Test
    void concurrentUpdatesCannotRemoveEveryEnabledAdministrator() throws Exception {
        AppUser second = new AppUser("second-admin", "second-admin@localhost", "x".repeat(60));
        second.setRole(Role.ADMIN);
        UserPrincipal secondAdmin = UserPrincipal.from(users.saveAndFlush(second));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> updateAfterStart(
                    start, secondAdmin.id(), admin));
            Future<String> secondUpdate = executor.submit(() -> updateAfterStart(
                    start, admin.id(), secondAdmin));
            start.countDown();

            List<String> outcomes = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    secondUpdate.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("updated", "last_admin_required");
        } finally {
            executor.shutdownNow();
        }

        assertThat(users.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(1);
    }

    private String updateAfterStart(CountDownLatch start, Long targetId,
                                    UserPrincipal principal) throws Exception {
        start.await();
        try {
            accounts.updateUser(targetId,
                    new AdminUserUpdateRequest(Role.ADMIN, false), principal);
            return "updated";
        } catch (ApiException e) {
            return e.getMessage();
        }
    }
}
