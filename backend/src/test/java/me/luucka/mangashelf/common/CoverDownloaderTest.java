package me.luucka.mangashelf.common;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoverDownloaderTest {
    private static final int LIMIT = 1024;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oversizedChunkedOrCompressedBodyIsAbortedWithoutWaitingForEof(boolean gzip) throws Exception {
        byte[] body = new byte[LIMIT + 1];
        if (gzip) {
            var output = new ByteArrayOutputStream();
            try (var compressor = new GZIPOutputStream(output)) { compressor.write(body); }
            body = output.toByteArray();
        }
        assertAborted(200, 0, body, gzip, "Cover exceeds byte limit");
    }

    @Test
    void oversizedContentLengthIsRejectedWithoutReadingTheBody() throws Exception {
        assertAborted(200, LIMIT + 100, new byte[]{1}, false, "Cover exceeds byte limit");
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 500})
    void rejectedStatusDoesNotDrainAnUnfinishedErrorBody(int status) throws Exception {
        assertAborted(status, 0, new byte[]{1}, false, "Remote server returned " + status);
    }

    private void assertAborted(int status, long length, byte[] bytes, boolean gzip, String error)
            throws Exception {
        var releaseServer = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover", exchange -> {
            try {
                if (gzip) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(status, length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().flush();
                // EOF is withheld until AFTER the client returns. Draining the
                // body or relying on its idle timeout cannot make this test pass.
                releaseServer.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Immediate client cancellation may race the fixture write.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (var client = CoverHttpConfiguration.create(resolver(server));
             var worker = Executors.newSingleThreadExecutor()) {
            try {
                var response = worker.submit(() -> {
                    try {
                        new CoverDownloader(client, LIMIT).fetch(url(server));
                        return "unexpected success";
                    } catch (IOException ex) {
                        return ex.getMessage();
                    }
                });
                assertThat(response.get(3, TimeUnit.SECONDS)).isEqualTo(error);
            } finally {
                releaseServer.countDown();
                server.stop(0);
            }
        }
    }

    @Test
    void acceptsACompleteBodyExactlyAtTheLimit() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = new byte[LIMIT];
        java.util.Arrays.fill(body, (byte) 42);
        server.createContext("/cover", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var client = CoverHttpConfiguration.create(resolver(server))) {
            assertThat(new CoverDownloader(client, LIMIT).fetch(url(server))).isEqualTo(body);
        } finally {
            server.stop(0);
        }
    }

    private DnsResolver resolver(HttpServer server) throws Exception {
        // Only this local transport fixture bypasses the production IP guard.
        var resolver = mock(DnsResolver.class);
        when(resolver.resolve("cover.example", server.getAddress().getPort()))
                .thenReturn(List.of(server.getAddress()));
        return resolver;
    }

    private String url(HttpServer server) {
        return "http://cover.example:" + server.getAddress().getPort() + "/cover";
    }
}
