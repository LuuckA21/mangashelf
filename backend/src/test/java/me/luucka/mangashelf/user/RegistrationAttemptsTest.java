package me.luucka.mangashelf.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAttemptsTest {

    @Test
    void boundsRegistrationsPerAddress() {
        RegistrationAttempts attempts = new RegistrationAttempts(2);

        assertThat(attempts.tryAcquire("203.0.113.10")).isTrue();
        assertThat(attempts.tryAcquire("203.0.113.10")).isTrue();
        assertThat(attempts.tryAcquire("203.0.113.10")).isFalse();
        assertThat(attempts.tryAcquire("203.0.113.11")).isTrue();
    }
}
