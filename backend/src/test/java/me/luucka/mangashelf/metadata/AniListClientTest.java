package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final AtomicLong nanos = new AtomicLong();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        nanos.set(0);
        client = new AniListClient(builder.build(), new RateLimiter(100, nanos::get), nanos::get);
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
    private static final String EMPTY = "{\"data\":{\"Page\":{\"media\":[]}}}";
    private static final String FOUND = """
            {"data":{"Page":{"media":[{"id":42,"title":{"romaji":"Berserk"}}]}}}
            """;

    @Test
    void searchCacheNormalisesKeysSeparatesLimitsAndExpires() {
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.search(" Berserk ", 10)).hasSize(1);
        assertThat(client.search("BERSERK", 10)).hasSize(1);
        assertThat(client.search("Berserk", 5)).isEmpty();
        assertThat(client.search("berserk", 5)).isEmpty();
        nanos.addAndGet(Duration.ofMinutes(5).toNanos());
        assertThat(client.search("Berserk", 10)).hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"data\":null}",
        "{\"data\":{\"Page\":{\"media\":null}}}",
        "{\"data\":{\"Page\":{\"media\":[null]}}}",
        "{\"data\":{\"Page\":{\"media\":[]}},\"errors\":[{\"status\":500}]}"
    })
    void invalidAndPartialResponsesAreNotCachedAsEmptyResults(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.search("Berserk", 10)).hasMessage("anilist_unavailable");
        nanos.addAndGet(Duration.ofSeconds(30).toNanos());
        assertThat(client.search("Berserk", 10)).hasSize(1);
        server.verify();
    }

    @Test
    void upstreamRateLimitBlocksSearchAndImportUntilRetryAfterButKeepsCachedResults() {
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "12"));
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        client.search("Berserk", 10);
        AniListException error = catchThrowableOfType(() -> client.search("One Piece", 10), AniListException.class);
        assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(error.getRetryAfterSeconds()).isEqualTo(12);
        assertThat(client.search("BERSERK", 10)).hasSize(1);
        assertThatThrownBy(() -> client.byId(42)).hasMessage("anilist_rate_limited");
        nanos.addAndGet(Duration.ofSeconds(12).toNanos());
        assertThat(client.byId(42).id()).isEqualTo(42);
        server.verify();
    }

    @Test
    void graphQlRateLimitAndForbiddenOutageHaveSpecificErrors() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"errors\":[{\"status\":429}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.search("Berserk", 10)).hasMessage("anilist_rate_limited");
        nanos.addAndGet(Duration.ofSeconds(60).toNanos());
        assertThatThrownBy(() -> client.search("Berserk", 10)).hasMessage("anilist_unavailable");
        server.verify();
    }

    @Test
    void retryAfterSupportsDatesAndMalformedFallback() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds(120).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME));
        assertThat(AniListClient.retryAfter(headers, 60)).isBetween(118, 120);
        headers.set("Retry-After", "invalid");
        assertThat(AniListClient.retryAfter(headers, 60)).isEqualTo(60);
    }

    @Test
    void emptyAndOversizedSearchesNeverReachUpstream() {
        assertThatThrownBy(() -> client.search("  ", 10)).hasMessage("anilist_invalid_search");
        assertThatThrownBy(() -> client.search("a".repeat(201), 10)).hasMessage("anilist_invalid_search");
        server.verify();
    }

    @Test
    void networkFailureStartsCooldownAndDoesNotPoisonCache() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new java.io.IOException("simulated outage");
        });
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.search("Berserk", 10)).hasMessage("anilist_unavailable");
        assertThatThrownBy(() -> client.search("Berserk", 10)).hasMessage("anilist_unavailable");
        nanos.addAndGet(Duration.ofSeconds(30).toNanos());
        assertThat(client.search("Berserk", 10)).hasSize(1);
        server.verify();
    }

    @Test
    void graphQlCooldownHonoursRetryAfterEvenOnHttpSuccess() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"errors\":[{\"status\":429}]}", MediaType.APPLICATION_JSON).header("Retry-After", "120"));
        AniListException error = catchThrowableOfType(() -> client.search("Berserk", 10), AniListException.class);
        assertThat(error.getRetryAfterSeconds()).isEqualTo(120);
        server.verify();
    }

    @Test
    void upstreamMissingDetailDoesNotBlockOtherSearches() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.byId(999)).hasMessage("anilist_media_not_found");
        assertThat(client.search("Berserk", 10)).hasSize(1);
        server.verify();
    }

}
