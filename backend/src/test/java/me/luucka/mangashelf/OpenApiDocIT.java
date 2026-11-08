package me.luucka.mangashelf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/openapi.json must match the API the code serves. After changing an endpoint, regenerate it:
 * {@code ./mvnw verify -Dit.test=OpenApiDocIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
 * -Dopenapi.update=true}
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OpenApiDocIT {

  private static final Path FILE = Path.of("../docs/openapi.json");

  @Autowired MockMvc mvc;

  @Test
  void docsOpenapiJsonMatchesTheCode() throws Exception {
    var served =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // sorted keys and fixed indentation, so the file only changes when the API does
    var mapper =
        JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    var expected = mapper.writeValueAsString(mapper.readValue(served, Object.class)) + "\n";

    if (Boolean.getBoolean("openapi.update")) {
      Files.writeString(FILE, expected);
      return;
    }
    assertThat(FILE)
        .withFailMessage("docs/openapi.json is missing: regenerate it (see OpenApiDocIT)")
        .exists();
    assertThat(Files.readString(FILE))
        .withFailMessage("docs/openapi.json is out of date: regenerate it (see OpenApiDocIT)")
        .isEqualTo(expected);
  }
}
