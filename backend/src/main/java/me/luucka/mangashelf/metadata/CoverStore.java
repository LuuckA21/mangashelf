package me.luucka.mangashelf.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
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

    /** Keeps the original format; unknown extensions are treated as JPEG. */
    private String extensionOf(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query > -1) path = path.substring(0, query);

        if (path.endsWith(".png")) return ".png";
        if (path.endsWith(".webp")) return ".webp";
        if (path.endsWith(".gif")) return ".gif";
        return ".jpg";
    }
}
