package me.luucka.mangashelf.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoverStoreTest {

    @TempDir
    Path directory;

    @Test
    void downloadsACoverToAnAtomicLocalFile() throws Exception {
        String remote = "https://8.8.8.8/cover.webp?size=large";
        byte[] bytes = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(remote))
                .andRespond(withSuccess(bytes, MediaType.parseMediaType("image/webp")));
        CoverStore store = new CoverStore(directory.toString(), builder.build());

        String path = store.store(remote, "anilist-42");

        assertThat(path).isEqualTo("/covers/anilist-42.webp");
        assertThat(Files.readAllBytes(directory.resolve("anilist-42.webp")))
                .containsExactly(bytes);
        assertThat(directory.resolve("anilist-42.webp.part")).doesNotExist();
        server.verify();
    }

    @Test
    void uploadedBytesUseTheirActualImageFormat() throws Exception {
        CoverStore store = new CoverStore(directory.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        String path = store.storeBytes(png, "manga-7");

        assertThat(path).isEqualTo("/covers/manga-7.png");
        assertThat(Files.readAllBytes(directory.resolve("manga-7.png")))
                .containsExactly(png);
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
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }
}
