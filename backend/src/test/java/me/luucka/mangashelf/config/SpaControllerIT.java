package me.luucka.mangashelf.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import me.luucka.mangashelf.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SpaControllerIT {

  @LocalServerPort int port;

  @Test
  void clientRoutesReturnTheAppShell() throws Exception {
    for (var path : new String[] {"/", "/login", "/editions/abc", "/admin/users"}) {
      var response = get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.body()).as(path).contains("<div id=\"root\">");
    }
  }

  @Test
  void apiStaysDenyByDefault() throws Exception {
    assertThat(get("/api/v1/unknown").statusCode()).isEqualTo(401);
    assertThat(get("/api").statusCode()).isEqualTo(401);
  }

  @Test
  void missingFilesAreNotFound() throws Exception {
    assertThat(get("/assets/x.js").statusCode()).isEqualTo(404);
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
