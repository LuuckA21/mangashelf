package me.luucka.mangashelf;

import me.luucka.mangashelf.user.AuthService;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UiLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPreferenceConcurrencyIT extends IntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService auth;

    @Test
    void languageChangeCannotUndoConcurrentSecurityChanges() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try (var securityChange = dataSource.getConnection()) {
            securityChange.setAutoCommit(false);
            int blocker;
            try (var query = securityChange.createStatement();
                 var result = query.executeQuery("SELECT pg_backend_pid()")) {
                result.next();
                blocker = result.getInt(1);
            }
            // Hold the same row lock used by account-security transactions.
            // A plain SELECT can still see the old values until this commits.
            try (var update = securityChange.prepareStatement("""
                    UPDATE app_user SET password_hash = ?, role = 'USER', enabled = false,
                        session_version = session_version + 1,
                        two_factor_secret = 'encrypted-test-secret', two_factor_last_step = 12345
                    WHERE id = ?
                    """)) {
                update.setString(1, "y".repeat(60));
                update.setLong(2, admin.id());
                update.executeUpdate();
            }
            var preference = worker.submit(() -> auth.updateLanguage(admin.id(), UiLanguage.EN));
            try {
                // Observe the DB lock rather than guessing thread order with a sleep.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (
                                SELECT 1 FROM pg_stat_activity
                                WHERE ? = ANY(pg_blocking_pids(pid)))
                            """, Boolean.class, blocker));
                    if (waiting) break;
                    if (preference.isDone()) preference.get();
                    Thread.sleep(20);
                }
                assertThat(waiting).as("preference update must overlap the security transaction").isTrue();
                securityChange.commit();
                preference.get(10, TimeUnit.SECONDS);

                var saved = users.findById(admin.id()).orElseThrow();
                assertThat(saved.getLanguage()).isEqualTo(UiLanguage.EN);
                assertThat(saved.getPasswordHash()).isEqualTo("y".repeat(60));
                assertThat(saved.getRole()).isEqualTo(Role.USER);
                assertThat(saved.isEnabled()).isFalse();
                assertThat(saved.getSessionVersion()).isEqualTo(admin.sessionVersion() + 1);
                assertThat(saved.getTwoFactorSecret()).isEqualTo("encrypted-test-secret");
                assertThat(saved.getTwoFactorLastStep()).isEqualTo(12345);
            } finally {
                securityChange.rollback();
            }
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }
}
