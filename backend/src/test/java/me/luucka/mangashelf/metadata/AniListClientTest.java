package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AniListClientTest {

    private static final String URL = "https://anilist.test/graphql";

    private MockRestServiceServer server;
    private AniListClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AniListClient(builder.build(), new RateLimiter(100));
    }

    @Test
    void searchSendsGraphQlVariablesAndMapsTheResponse() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"search\":\"Berserk\"")))
                .andExpect(content().string(containsString("\"perPage\":7")))
                .andRespond(withSuccess("""
                        {"data":{"Page":{"media":[{
                          "id": 42,
                          "idMal": 2,
                          "title": {
                            "romaji": "Berserk",
                            "english": "Berserk",
                            "native": "ベルセルク"
                          },
                          "description": "A swordsman.",
                          "status": "RELEASING",
                          "genres": ["Action", "Drama"],
                          "volumes": 43,
                          "startDate": {"year": 1989},
                          "coverImage": {
                            "extraLarge": "https://images.test/berserk.webp",
                            "large": "https://images.test/berserk.jpg"
                          },
                          "staff": {"edges": []}
                        }]}}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.search("Berserk", 7);

        assertThat(result).singleElement().satisfies(media -> {
            assertThat(media.id()).isEqualTo(42);
            assertThat(media.title().nativeTitle()).isEqualTo("ベルセルク");
            assertThat(media.genres()).containsExactly("Action", "Drama");
            assertThat(media.startDate().year()).isEqualTo(1989);
        });
        server.verify();
    }

    @Test
    void byIdReportsAMissingWork() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"id\":999")))
                .andRespond(withSuccess("{\"data\":{\"Page\":{\"media\":[]}}}",
                        MediaType.APPLICATION_JSON));

        ApiException error = catchThrowableOfType(
                () -> client.byId(999), ApiException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error).hasMessage("anilist_media_not_found");
        server.verify();
    }

    @Test
    void upstreamFailuresHaveAStableGatewayError() {
        server.expect(once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ApiException error = catchThrowableOfType(
                () -> client.search("Berserk", 10), ApiException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(error).hasMessage("anilist_unavailable");
        server.verify();
    }
}
