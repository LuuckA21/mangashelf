package me.luucka.mangashelf.common;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;

/** Bounded reads that abort rejected responses instead of draining their bodies. */
final class CoverDownloader {
    private final CloseableHttpClient client;
    private final int maximumBytes;

    CoverDownloader(CloseableHttpClient client, int maximumBytes) {
        this.client = client;
        this.maximumBytes = maximumBytes;
    }

    byte[] fetch(String url) throws IOException {
        var request = new HttpGet(URI.create(url));
        boolean consumed = false;
        // executeOpen deliberately leaves body ownership here. RestClient and
        // response handlers drain unread bytes when closing/releasing a response,
        // which would defeat our limit for oversized or endless error bodies.
        try (var response = client.executeOpen(null, request, null)) {
            try {
                if (response.getCode() < 200 || response.getCode() >= 300) {
                    throw new IOException("Remote server returned " + response.getCode());
                }
                var entity = response.getEntity();
                if (entity == null) {
                    consumed = true;
                    return new byte[0];
                }
                if (entity.getContentLength() > maximumBytes) {
                    throw new IOException("Cover exceeds byte limit");
                }
                // Also bounds chunked and transparently decompressed responses.
                byte[] bytes = entity.getContent().readNBytes(maximumBytes + 1);
                if (bytes.length > maximumBytes) {
                    throw new IOException("Cover exceeds byte limit");
                }
                consumed = true;
                return bytes;
            } finally {
                // Cancel BEFORE close: closing the entity alone can consume the
                // rest of the stream to reuse the connection. Successful bounded
                // responses may still return their connection to the pool.
                if (!consumed) request.cancel();
            }
        }
    }
}
