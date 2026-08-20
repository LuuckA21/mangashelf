package me.luucka.mangashelf;

import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AuthService;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Registration rules that depend on real transaction boundaries. */
class AuthIT extends IntegrationTest {

    @Autowired
    private AuthService auth;

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
                "a sufficiently long password");
    }
}
