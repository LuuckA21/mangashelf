package me.luucka.mangashelf.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads cover images and keeps them on local disk.
 *
 * <p>Without this the catalogue hotlinks AniList's CDN: the covers stop
 * working the day those URLs change, every visitor's address is handed to a
 * third party, and the interface is served on somebody else's bandwidth.
 */
@Component
public class CoverStore {

    private static final Logger log = LoggerFactory.getLogger(CoverStore.class);

    /** Covers are a few hundred kilobytes; anything larger is not a cover. */
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8_192;
    private static final long MAX_PIXELS = 16_000_000;

    /** Public path the files are served from — see the nginx config. */
    private static final String PUBLIC_PREFIX = "/covers/";

    private final Path directory;
    private final RestClient http;
    private final Set<String> allowedHosts;

    @Autowired
    public CoverStore(@Value("${app.covers-dir}") String coversDir,
                      @Value("${app.covers.allowed-hosts:s4.anilist.co}")
                      List<String> allowedHosts) {
        this(coversDir, downloadClient(), Set.copyOf(allowedHosts));
    }

    /** Test seams for local image validation and mock HTTP downloads. */
    CoverStore(String coversDir) {
        this(coversDir, downloadClient(), Set.of("s4.anilist.co"));
    }

    CoverStore(String coversDir, RestClient http, Set<String> allowedHosts) {
        this.directory = Path.of(coversDir);
        this.http = http;
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Fetches a remote image and returns the local path to serve it from.
     *
     * <p>Returns null when anything goes wrong. A missing cover must never
     * fail an import, but retaining an arbitrary remote URL would make every
     * visitor contact that host and allow it to track them.
     *
     * @param name stable file name without extension, typically the external id
     */
    public String store(String remoteUrl, String name) {
        if (remoteUrl == null || remoteUrl.isBlank()) return null;
        if (!isFetchable(remoteUrl)) {
            log.warn("Refused to fetch a cover from {}", remoteUrl);
            return null;
        }

        try {
            byte[] bytes = http.get().uri(remoteUrl).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IOException("Remote server returned " + response.getStatusCode());
                }
                // Reading one byte beyond the ceiling detects an oversized
                // body without first allocating the attacker's whole reply.
                return response.getBody().readNBytes(MAX_BYTES + 1);
            });

            if (bytes == null || bytes.length == 0) {
                log.warn("Empty cover from {}", remoteUrl);
                return null;
            }
            if (bytes.length > MAX_BYTES) {
                log.warn("Cover too large ({} bytes) from {}", bytes.length, remoteUrl);
                return null;
            }

            StoredImage image = validatedImage(bytes);
            String fileName = safeName(name) + image.extension();
            writeAtomically(fileName, image.bytes());

            return PUBLIC_PREFIX + fileName;

        } catch (IOException | RuntimeException e) {
            log.warn("Could not store the cover from {}: {}", remoteUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Stores bytes that arrived from an upload rather than a URL.
     *
     */
    public String storeBytes(byte[] bytes, String name) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("empty_file");
        }
        if (bytes.length > MAX_BYTES) {
            throw ApiException.badRequest("file_too_large");
        }
        try {
            StoredImage image = validatedImage(bytes);
            String fileName = safeName(name) + image.extension();
            writeAtomically(fileName, image.bytes());
            return PUBLIC_PREFIX + fileName;
        } catch (IOException e) {
            log.warn("Could not store the uploaded cover: {}", e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "cover_not_stored");
        }
    }

    /**
     * Parses and fully decodes the first frame before re-encoding it. This
     * rejects files that merely prepend an image signature to arbitrary
     * content, and the dimension ceiling stops small decompression bombs from
     * allocating an unbounded bitmap. WebP is deliberately not accepted:
     * the JDK has no built-in decoder with which to perform the same check.
     */
    private StoredImage validatedImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (input == null) throw ApiException.badRequest("not_an_image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiException.badRequest("not_an_image");

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw ApiException.badRequest("image_dimensions_too_large");
                }

                String format = normalisedFormat(reader.getFormatName());
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw ApiException.badRequest("not_an_image");

                ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
                if (!ImageIO.write(decoded, format, output)) {
                    throw ApiException.badRequest("not_an_image");
                }
                byte[] normalised = output.toByteArray();
                if (normalised.length > MAX_BYTES) {
                    throw ApiException.badRequest("file_too_large");
                }
                return new StoredImage(normalised,
                        format.equals("jpeg") ? ".jpg" : "." + format);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("not_an_image");
        }
    }

    private String normalisedFormat(String formatName) {
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            default -> throw ApiException.badRequest("not_an_image");
        };
    }

    private String safeName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9._-]+")) {
            throw ApiException.badRequest("invalid_cover_name");
        }
        return name;
    }

    private void writeAtomically(String fileName, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, fileName + "-", ".part");
        try {
            Files.write(temp, bytes);
            Files.move(temp, directory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static RestClient downloadClient() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // A redirect is another user-controlled destination. Refuse
                // it instead of bypassing the private-address check above.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requests).build();
    }

    /**
     * True when the value is something to fetch rather than a path we already
     * serve, so that saving a form does not re-download an existing cover.
     */
    public boolean isRemote(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * Whether the server should go and get this address.
     *
     * <p>The URL comes from a form, so without this the application would
     * fetch whatever it is handed — including addresses only it can reach.
     * On a self-hosted box that means the database, the proxy's admin panel
     * and everything else on the LAN, turned into a probe by pasting a link.
     * Only explicitly trusted image hosts are accepted. Besides reducing the
     * attack surface, the allow-list removes the DNS-rebinding gap between
     * validation and the HTTP client's own hostname resolution.
     */
    private boolean isFetchable(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                return false;
            }
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                return false;
            }

            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) return false;
            for (InetAddress address : addresses) {
                if (isUnsafeAddress(address)) {
                    return false;
                }
            }
            return true;

        } catch (IllegalArgumentException | UnknownHostException e) {
            return false;
        }
    }

    private boolean isUnsafeAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            // Non-public IPv4 ranges not covered by isSiteLocalAddress:
            // shared carrier space, protocol assignments, benchmark nets,
            // documentation nets and future/reserved space.
            return first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && (third == 0 || third == 2))
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)
                    || first >= 240;
        }

        // fc00::/7 is IPv6 unique-local space. Java's site-local check only
        // recognises the deprecated fec0::/10 range, so it must be explicit.
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    private record StoredImage(byte[] bytes, String extension) {
    }

}
