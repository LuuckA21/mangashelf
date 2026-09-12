package me.luucka.mangashelf.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoverStoreTest {

    @TempDir
    Path directory;

    @Test
    void downloadsACoverToAnAtomicLocalFile() throws Exception {
        String remote = "https://8.8.8.8/cover.png?size=large";
        byte[] bytes = image("png", 20, 30);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(remote))
                .andRespond(withSuccess(bytes, MediaType.IMAGE_PNG));
        CoverStore store = new CoverStore(
                directory.toString(), builder.build(), Set.of("8.8.8.8"));

        String path = store.store(remote, "anilist-42");

        assertThat(path).isEqualTo("/covers/anilist-42.png");
        byte[] stored = Files.readAllBytes(directory.resolve("anilist-42.png"));
        assertPublicReadPermissions(directory.resolve("anilist-42.png"));
        assertThat(ImageIO.read(new ByteArrayInputStream(stored)))
                .extracting(BufferedImage::getWidth, BufferedImage::getHeight)
                .containsExactly(20, 30);
        try (var files = Files.list(directory)) {
            assertThat(files.map(pathEntry -> pathEntry.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".part"));
        }
        server.verify();
    }

    @Test
    void uploadedBytesUseTheirActualImageFormat() throws Exception {
        CoverStore store = new CoverStore(directory.toString());
        byte[] png = image("png", 10, 12);

        String path = store.storeBytes(png, "manga-7");

        assertThat(path).isEqualTo("/covers/manga-7.png");
        assertPublicReadPermissions(directory.resolve("manga-7.png"));
        assertThat(ImageIO.read(directory.resolve("manga-7.png").toFile()))
                .extracting(BufferedImage::getWidth, BufferedImage::getHeight)
                .containsExactly(10, 12);
    }

    @Test
    void replacingAnOwnerOnlyCoverPublishesAReadableImage() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path existing = directory.resolve("manga-7.png");
        Files.write(existing, image("png", 1, 1));
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("rw-------"));

        new CoverStore(directory.toString()).storeBytes(image("png", 10, 12), "manga-7");

        assertPublicReadPermissions(existing);
        assertThat(ImageIO.read(existing.toFile()).getWidth()).isEqualTo(10);
    }

    @Test
    void startupRepairsExistingCoversWithoutChangingBytesOrOtherFiles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.getFileStore(directory).supportsFileAttributeView("posix"));
        byte[] bytes = image("png", 20, 30);
        Path cover = Files.write(directory.resolve("anilist-30598.png"), bytes);
        Path notes = Files.writeString(directory.resolve("notes.txt"), "private");
        Path pending = Files.write(directory.resolve("upload.part"), bytes);
        Path nested = Files.createDirectory(directory.resolve("nested.png"));
        Path nestedCover = Files.write(nested.resolve("cover.png"), bytes);
        Files.createSymbolicLink(directory.resolve("linked.png"), notes);
        var ownerOnly = PosixFilePermissions.fromString("rw-------");
        for (Path file : new Path[]{cover, notes, pending, nestedCover}) {
            Files.setPosixFilePermissions(file, ownerOnly);
        }

        CoverStore store = new CoverStore(directory.toString());
        store.repairExistingCoverPermissions();
        store.repairExistingCoverPermissions(); // Safe on every restart.

        assertPublicReadPermissions(cover);
        assertThat(Files.readAllBytes(cover)).isEqualTo(bytes);
        for (Path file : new Path[]{notes, pending, nestedCover}) {
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(ownerOnly);
        }
        assertThat(Files.isSymbolicLink(directory.resolve("linked.png"))).isTrue();
    }

    @Test
    void startupDoesNotCreateAMissingCoverDirectory() throws Exception {
        Path missing = directory.resolve("not-created-yet");

        new CoverStore(missing.toString()).repairExistingCoverPermissions();

        assertThat(missing).doesNotExist();
    }

    private void assertPublicReadPermissions(Path file) throws Exception {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(file))
                    .isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test
    void rejectsEmptyAndOversizedUploads() {
        CoverStore store = new CoverStore(directory.toString());

        assertThatThrownBy(() -> store.storeBytes(new byte[0], "empty"))
                .isInstanceOf(ApiException.class)
                .hasMessage("empty_file");
        assertThatThrownBy(() -> store.storeBytes(
                new byte[5 * 1024 * 1024 + 1], "large"))
                .isInstanceOf(ApiException.class)
                .hasMessage("file_too_large");
        assertThatThrownBy(() -> store.storeBytes(new byte[]{1, 2, 3}, "text"))
                .isInstanceOf(ApiException.class)
                .hasMessage("not_an_image");
        assertThatThrownBy(() -> store.storeBytes(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'},
                "unverified-webp"))
                .isInstanceOf(ApiException.class)
                .hasMessage("not_an_image");
        assertThatThrownBy(() -> store.storeBytes(
                image("png", 8_193, 1), "too-wide"))
                .isInstanceOf(ApiException.class)
                .hasMessage("image_dimensions_too_large");
    }

    @Test
    void refusesLocalAndUnsupportedRemoteAddresses() throws Exception {
        CoverStore store = new CoverStore(directory.toString());

        assertThat(store.store("http://127.0.0.1/admin", "loopback"))
                .isNull();
        assertThat(store.store("http://192.168.1.10/cover.jpg", "lan"))
                .isNull();
        assertThat(store.store("http://100.64.0.1/cover.jpg", "carrier"))
                .isNull();
        assertThat(store.store("http://[fc00::1]/cover.jpg", "unique-local"))
                .isNull();
        assertThat(store.store("file:///etc/passwd", "file"))
                .isNull();
        assertThat(store.store("https://8.8.8.8/cover.jpg", "not-allowed"))
                .isNull();
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
}
