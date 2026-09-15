package me.luucka.mangashelf.common;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CoverHttpConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.0.1",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "192.0.2.1",
            "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1",
            "::", "::1", "fc00::1", "fd00::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1"})
    void rejectsNonPublicAnswersIncludingMixedDnsResults(String unsafe) throws Exception {
        var resolver = new CoverHttpConfiguration.PublicAddressesOnly(host -> new InetAddress[]{
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName(unsafe)});

        assertThatThrownBy(() -> resolver.resolve("cover.example"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessage("Refused non-public cover destination");
        assertThatThrownBy(() -> resolver.resolve("cover.example", 443))
                .isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> resolver.resolveCanonicalHostname("cover.example"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void returnsTheValidatedAddressesWithoutResolvingAgain() throws Exception {
        var lookup = mock(CoverHttpConfiguration.Lookup.class);
        InetAddress[] approved = {InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("2606:4700:4700::1111")};
        when(lookup.resolve("cover.example")).thenReturn(approved)
                .thenReturn(new InetAddress[]{InetAddress.getLoopbackAddress()});
        var resolver = new CoverHttpConfiguration.PublicAddressesOnly(lookup);

        var endpoints = resolver.resolve("cover.example", 443);

        assertThat(endpoints).extracting(endpoint -> endpoint.getAddress())
                .containsExactly(approved);
        assertThat(endpoints).allMatch(endpoint -> !endpoint.isUnresolved() && endpoint.getPort() == 443);
        verify(lookup, times(1)).resolve("cover.example");
        // A later connection is validated again, even after an earlier safe answer.
        assertThatThrownBy(() -> resolver.resolve("cover.example"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void canonicalNamePreservesTheOriginalTlsHostname() throws Exception {
        var lookup = mock(CoverHttpConfiguration.Lookup.class);
        when(lookup.resolve("cover.example"))
                .thenReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8")});

        assertThat(new CoverHttpConfiguration.PublicAddressesOnly(lookup)
                .resolveCanonicalHostname("cover.example")).isEqualTo("cover.example");
        verify(lookup, times(1)).resolve("cover.example");
    }

    @Test
    void rejectsAnEmptyDnsAnswer() {
        var resolver = new CoverHttpConfiguration.PublicAddressesOnly(host -> new InetAddress[0]);
        assertThatThrownBy(() -> resolver.resolve("cover.example"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void realHttpClientConsultsTheGuardBeforeConnectingAndDoesNotRetry() throws Exception {
        var lookup = mock(CoverHttpConfiguration.Lookup.class);
        when(lookup.resolve("rebound.example"))
                .thenReturn(new InetAddress[]{InetAddress.getLoopbackAddress()});
        try (var client = CoverHttpConfiguration.create(
                new CoverHttpConfiguration.PublicAddressesOnly(lookup))) {
            assertThatThrownBy(() -> client.execute(new HttpGet("https://rebound.example/cover.png"),
                    response -> response.getCode()))
                    .isInstanceOf(UnknownHostException.class)
                    .hasMessage("Refused non-public cover destination");
        }
        verify(lookup, times(1)).resolve("rebound.example");
    }

    @Test
    void realHttpClientDoesNotFollowRedirects() throws Exception {
        // Only this transport test substitutes the guard to reach a local fixture.
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var redirected = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/cover", exchange -> {
            exchange.getResponseHeaders().add("Location", "/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/private", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        var resolver = mock(org.apache.hc.client5.http.DnsResolver.class);
        when(resolver.resolve("cover.example", server.getAddress().getPort()))
                .thenReturn(java.util.List.of(server.getAddress()));
        try (var client = CoverHttpConfiguration.create(resolver)) {
            int status = client.execute(new HttpGet("http://cover.example:"
                    + server.getAddress().getPort() + "/cover"), response -> response.getCode());
            assertThat(status).isEqualTo(302);
            assertThat(redirected).hasValue(0);
        } finally {
            server.stop(0);
        }
    }
}
