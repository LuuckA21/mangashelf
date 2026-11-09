package me.luucka.mangashelf.catalog;

import static me.luucka.mangashelf.users.AuthTestSupport.session;
import static me.luucka.mangashelf.users.AuthTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import me.luucka.mangashelf.TestDatabase;
import me.luucka.mangashelf.TestcontainersConfig;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.AppUserRepository;
import me.luucka.mangashelf.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CoverIT {

  private static final String PASSWORD = "correct-horse-1";

  @TempDir static Path uploads;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("app.uploads-dir", () -> uploads.toString());
  }

  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  Cookie admin;
  Cookie mario;
  Cookie luigi;
  String editionId;

  @BeforeEach
  void seed() throws Exception {
    TestDatabase.clean(jdbc);
    try (Stream<Path> files = Files.list(uploads)) {
      for (var f : files.toList()) {
        Files.delete(f);
      }
    }
    users.save(new AppUser("Admin", encoder.encode(PASSWORD), Role.ADMIN, false, Instant.now()));
    users.save(new AppUser("Mario", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    users.save(new AppUser("Luigi", encoder.encode(PASSWORD), Role.USER, false, Instant.now()));
    admin = session(mvc, "Admin", PASSWORD);
    mario = session(mvc, "Mario", PASSWORD);
    luigi = session(mvc, "Luigi", PASSWORD);
    var series =
        JsonPath.<String>read(
            send(post("/api/v1/series"), mario, "{\"title\":\"One Piece\"}")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    editionId =
        JsonPath.read(
            send(
                    post("/api/v1/series/" + series + "/editions"),
                    mario,
                    "{\"name\":\"Blu\",\"type\":\"REGULAR\",\"status\":\"ONGOING\",\"volumeCount\":5}")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
  }

  private ResultActions send(MockHttpServletRequestBuilder req, Cookie session, String json)
      throws Exception {
    if (json != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    return mvc.perform(withAuth(req, session));
  }

  private <B extends AbstractMockHttpServletRequestBuilder<B>> B withAuth(B req, Cookie s)
      throws Exception {
    var token = xsrf(mvc);
    return req.cookie(s, token).header("X-XSRF-TOKEN", token.getValue());
  }

  private ResultActions putCover(Cookie session, byte[] bytes) throws Exception {
    var file = new MockMultipartFile("file", "c.png", "image/png", bytes);
    return mvc.perform(
        withAuth(
            multipart(HttpMethod.PUT, "/api/v1/editions/" + editionId + "/cover").file(file),
            session));
  }

  private static byte[] png() throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(60, 90, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private long fileCount() throws IOException {
    try (Stream<Path> files = Files.list(uploads)) {
      return files.count();
    }
  }

  private String coverFile() {
    return jdbc.queryForObject(
        "SELECT cover_file FROM editions WHERE id = ?::uuid", String.class, editionId);
  }

  @Test
  void creatorUploadsAndEveryoneReads() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/editions/" + editionId + "/cover").cookie(luigi))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", containsString("private")));
    mvc.perform(get("/api/v1/editions/" + editionId).cookie(luigi))
        .andExpect(jsonPath("$.hasCover").value(true));
    assertThat(fileCount()).isEqualTo(1);
  }

  @Test
  void otherNonAdminUserCannotUploadIs403() throws Exception {
    putCover(luigi, png()).andExpect(status().isForbidden());
    assertThat(fileCount()).isZero();
  }

  @Test
  void coverChangeBumpsVersion() throws Exception {
    var before = version();
    putCover(mario, png()).andExpect(status().isNoContent());
    assertThat(version()).isGreaterThan(before);
  }

  private long version() {
    return jdbc.queryForObject(
        "SELECT version FROM editions WHERE id = ?::uuid", Long.class, editionId);
  }

  @Test
  void adminCanDeleteCover() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());
    send(delete("/api/v1/editions/" + editionId + "/cover"), admin, null)
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/editions/" + editionId + "/cover").cookie(luigi))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCan() throws Exception {
    putCover(admin, png()).andExpect(status().isNoContent());
  }

  @Test
  void invalidImageIs400AndLeavesNoFile() throws Exception {
    putCover(mario, "hello".getBytes()).andExpect(status().isBadRequest());
    assertThat(fileCount()).isZero();
  }

  @Test
  void tooLargeIs413() throws Exception {
    var boundary = "xBOUNDARYx";
    var head =
        ("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"c.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .formatted(boundary)
            .getBytes();
    var tail = "\r\n--%s--\r\n".formatted(boundary).getBytes();
    var body = new ByteArrayOutputStream();
    body.write(head);
    body.write(new byte[5 * 1024 * 1024 + 1]);
    body.write(tail);
    var token = xsrf(mvc).getValue();
    var request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/editions/" + editionId + "/cover"))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("X-XSRF-TOKEN", token)
            .header("Cookie", "SESSION=" + mario.getValue() + "; XSRF-TOKEN=" + token)
            .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isEqualTo(413);
  }

  @Test
  void replacingDeletesOldFile() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());
    var first = coverFile();

    putCover(mario, png()).andExpect(status().isNoContent());

    assertThat(coverFile()).isNotEqualTo(first);
    assertThat(fileCount()).isEqualTo(1);
    assertThat(uploads.resolve(first)).doesNotExist();
  }

  @Test
  void deletingEditionDeletesFile() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());

    send(delete("/api/v1/editions/" + editionId), admin, null).andExpect(status().isNoContent());

    assertThat(fileCount()).isZero();
  }

  @Test
  void noCoverIs404() throws Exception {
    mvc.perform(get("/api/v1/editions/" + editionId + "/cover").cookie(luigi))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingFileOnDiskIs404() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());
    Files.delete(uploads.resolve(coverFile()));

    mvc.perform(get("/api/v1/editions/" + editionId + "/cover").cookie(luigi))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteCoverIs204ThenGetIs404() throws Exception {
    putCover(mario, png()).andExpect(status().isNoContent());

    send(delete("/api/v1/editions/" + editionId + "/cover"), luigi, null)
        .andExpect(status().isForbidden());
    send(delete("/api/v1/editions/" + editionId + "/cover"), mario, null)
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/editions/" + editionId + "/cover").cookie(luigi))
        .andExpect(status().isNotFound());
    assertThat(fileCount()).isZero();
  }
}
