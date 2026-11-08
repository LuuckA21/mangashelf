package me.luucka.mangashelf.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and relative server for the generated docs/openapi.json (served only outside prod). */
@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MangaShelf API")
                .version("v1")
                .description(
                    "Session cookie + XSRF header authentication; see docs/api.md for the guide."))
        .servers(List.of(new Server().url("/")));
  }
}
