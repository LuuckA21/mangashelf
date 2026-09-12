package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountEmailRequestsTest {
    @Test
    void limitsAddressesAndCoalescesRecipientsAcrossRequestTypes() {
        AccountEmailService service = mock(AccountEmailService.class);
        try (var requests = new Requests(service)) {
            requests.value.limit("127.0.0.1");
            requests.value.limit("127.0.0.1");
            assertThatThrownBy(() -> requests.value.limit("127.0.0.1")).isInstanceOf(ApiException.class);
            requests.value.limit("127.0.0.2");
            requests.value.submit("reader@example.test", false);
            requests.value.submit("reader@example.test", true);
            verify(service, timeout(2000)).requestEmail("reader@example.test", false);
            verify(service, never()).requestEmail("reader@example.test", true);
        }
    }

    private static class Requests implements AutoCloseable {
        final AccountEmailRequests value;
        Requests(AccountEmailService service) { value = new AccountEmailRequests(service, 2); }
        public void close() { value.close(); }
    }
}
