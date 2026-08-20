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
        String remote = "https://203.0.113.10/cover.webp?size=large";
        byte[] bytes = {1, 2, 3, 4};
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
    void uploadedBytesKeepOnlyAKnownImageExtension() throws Exception {
        CoverStore store = new CoverStore(directory.toString());

        String path = store.storeBytes(new byte[]{9, 8, 7},
                "manga-7", store.extensionOf("scan.PNG?download=1"));

        assertThat(path).isEqualTo("/covers/manga-7.png");
        assertThat(Files.readAllBytes(directory.resolve("manga-7.png")))
                .containsExactly(9, 8, 7);
        assertThat(store.extensionOf("cover.svg")).isEqualTo(".jpg");
    }

    @Test
    void rejectsEmptyAndOversizedUploads() {
        CoverStore store = new CoverStore(directory.toString());

        assertThatThrownBy(() -> store.storeBytes(new byte[0], "empty", ".jpg"))
                .isInstanceOf(ApiException.class)
                .hasMessage("empty_file");
        assertThatThrownBy(() -> store.storeBytes(
                new byte[5 * 1024 * 1024 + 1], "large", ".jpg"))
                .isInstanceOf(ApiException.class)
                .hasMessage("file_too_large");
    }

    @Test
    void refusesLocalAndUnsupportedRemoteAddresses() throws Exception {
        CoverStore store = new CoverStore(directory.toString());

        assertThat(store.store("http://127.0.0.1/admin", "loopback"))
                .isEqualTo("http://127.0.0.1/admin");
        assertThat(store.store("http://192.168.1.10/cover.jpg", "lan"))
                .isEqualTo("http://192.168.1.10/cover.jpg");
        assertThat(store.store("file:///etc/passwd", "file"))
                .isEqualTo("file:///etc/passwd");
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }
}
