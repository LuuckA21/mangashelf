package me.luucka.mangashelf.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

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

    /** Public path the files are served from — see the nginx config. */
    private static final String PUBLIC_PREFIX = "/covers/";

    private final Path directory;
    private final RestClient http = RestClient.create();

    public CoverStore(@Value("${app.covers-dir}") String coversDir) {
        this.directory = Path.of(coversDir);
    }

    /**
     * Fetches a remote image and returns the local path to serve it from.
     *
     * <p>Returns the original URL when anything goes wrong. A missing cover
     * must never fail an import: the metadata is the point, the picture is
     * a convenience.
     *
     * @param name stable file name without extension, typically the external id
     */
    public String store(String remoteUrl, String name) {
        if (remoteUrl == null || remoteUrl.isBlank()) return null;
        if (!isFetchable(remoteUrl)) {
            log.warn("Refused to fetch a cover from {}", remoteUrl);
            return remoteUrl;
        }

        try {
            byte[] bytes = http.get().uri(remoteUrl).retrieve().body(byte[].class);

            if (bytes == null || bytes.length == 0) {
                log.warn("Empty cover from {}", remoteUrl);
                return remoteUrl;
            }
            if (bytes.length > MAX_BYTES) {
                log.warn("Cover too large ({} bytes) from {}", bytes.length, remoteUrl);
                return remoteUrl;
            }

            Files.createDirectories(directory);
            String fileName = name + extensionOf(remoteUrl);

            // Written beside the target and moved into place: a download cut
            // short would otherwise leave a truncated image that looks valid
            // to the browser until it tries to decode it.
            Path temp = directory.resolve(fileName + ".part");
            Files.write(temp, bytes);
            Files.move(temp, directory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);

            return PUBLIC_PREFIX + fileName;

        } catch (IOException | RuntimeException e) {
            log.warn("Could not store the cover from {}: {}", remoteUrl, e.getMessage());
            return remoteUrl;
        }
    }

    /**
     * Stores bytes that arrived from an upload rather than a URL.
     *
     * @param extension including the dot, e.g. {@code .jpg}
     */
    public String storeBytes(byte[] bytes, String name, String extension) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("empty_file");
        }
        if (bytes.length > MAX_BYTES) {
            throw ApiException.badRequest("file_too_large");
        }
        try {
            Files.createDirectories(directory);
            String fileName = name + extension;
            Path temp = directory.resolve(fileName + ".part");
            Files.write(temp, bytes);
            Files.move(temp, directory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
            return PUBLIC_PREFIX + fileName;
        } catch (IOException e) {
            log.warn("Could not store the uploaded cover: {}", e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "cover_not_stored");
        }
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
     * Covers live on the public internet, so nothing legitimate is lost by
     * refusing the rest.
     */
    private boolean isFetchable(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            if (uri.getHost() == null) return false;

            InetAddress address = InetAddress.getByName(uri.getHost());
            return !address.isLoopbackAddress()
                    && !address.isSiteLocalAddress()
                    && !address.isLinkLocalAddress()
                    && !address.isAnyLocalAddress()
                    && !address.isMulticastAddress();

        } catch (IllegalArgumentException | UnknownHostException e) {
            return false;
        }
    }

    /** Keeps the original format; unknown extensions are treated as JPEG. */
    public String extensionOf(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query > -1) path = path.substring(0, query);

        if (path.endsWith(".png")) return ".png";
        if (path.endsWith(".webp")) return ".webp";
        if (path.endsWith(".gif")) return ".gif";
        return ".jpg";
    }
}
