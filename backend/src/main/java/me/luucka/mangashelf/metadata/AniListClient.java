package me.luucka.mangashelf.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.metadata.dto.AniListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Reads public metadata with a bounded search cache and shared upstream cooldown. */
@Component
public class AniListClient {

    private static final Logger log = LoggerFactory.getLogger(AniListClient.class);

    /**
     * Only the fields the catalogue actually stores are requested. Asking
     * for less is both faster and less likely to break when the schema
     * grows around us.
     */
    private static final String SEARCH_QUERY = """
            query ($search: String, $perPage: Int) {
              Page(page: 1, perPage: $perPage) {
                media(search: $search, type: MANGA, sort: SEARCH_MATCH) {
                  id
                  idMal
                  title { romaji english native }
                  description(asHtml: false)
                  status
                  genres
                  volumes
                  startDate { year }
                  coverImage { extraLarge large }
                  staff(perPage: 4) { edges { role node { name { full } } } }
                }
              }
            }
            """;

    private static final String BY_ID_QUERY = """
            query ($id: Int) {
              Page(page: 1, perPage: 1) {
                media(id: $id, type: MANGA) {
                  id
                  idMal
                  title { romaji english native }
                  description(asHtml: false)
                  status
                  genres
                  volumes
                  startDate { year }
                  coverImage { extraLarge large }
                  staff(perPage: 4) { edges { role node { name { full } } } }
                }
              }
            }
            """;

    private final RestClient http;
    private final RateLimiter limiter;
    private record SearchKey(String term, int limit) {}
    private final Cache<SearchKey, List<AniListResponse.Media>> searches;

    /** Builds an isolated client with finite network timeouts. */
    @Autowired
    public AniListClient(@Value("${app.metadata.anilist-url}") String url,
                         @Value("${app.metadata.anilist-requests-per-minute}") int perMinute) {
        this(metadataClient(url), new RateLimiter(perMinute));
    }

    /** Test seam for a client backed by Spring's mock HTTP server. */
    AniListClient(RestClient http, RateLimiter limiter) {
        this(http, limiter, Ticker.systemTicker());
    }

    AniListClient(RestClient http, RateLimiter limiter, Ticker ticker) {
        this.http = http;
        this.limiter = limiter;
        this.searches = Caffeine.newBuilder().maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5)).ticker(ticker).build();
    }

    private static RestClient metadataClient(String url) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(requests)
                .build();
    }

    public List<AniListResponse.Media> search(String term, int limit) {
        String trimmed = term.strip();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw ApiException.badRequest("anilist_invalid_search");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 25));
        SearchKey key = new SearchKey(trimmed.toLowerCase(Locale.ROOT), boundedLimit);
        // Caffeine coalesces concurrent loads for the same query. Exceptions
        // are not cached; only raw metadata is cached, never catalogue flags.
        return searches.get(key, ignored -> execute(SEARCH_QUERY,
                Map.of("search", trimmed, "perPage", boundedLimit)));
    }

    public AniListResponse.Media byId(int anilistId) {
        List<AniListResponse.Media> media = execute(BY_ID_QUERY, Map.of("id", anilistId));
        if (media.isEmpty()) {
            throw ApiException.notFound("anilist_media_not_found");
        }
        return media.getFirst();
    }

    private List<AniListResponse.Media> execute(String query, Map<String, Object> variables) {
        limiter.acquire();
        try {
            var reply = http.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .toEntity(AniListResponse.class);
            AniListResponse response = reply.getBody();

            if (response != null && response.errors() != null && !response.errors().isEmpty()) {
                if (variables.containsKey("id") && response.errors().stream()
                        .allMatch(error -> error != null && Integer.valueOf(404).equals(error.status()))) {
                    throw ApiException.notFound("anilist_media_not_found");
                }
                boolean limited = response.errors().stream()
                        .anyMatch(error -> error != null && Integer.valueOf(429).equals(error.status()));
                throw limiter.pause(limited ? "anilist_rate_limited" : "anilist_unavailable",
                        retryAfter(reply.getHeaders(), limited ? 60 : 30));
            }
            if (response == null || response.data() == null || response.data().page() == null
                    || response.data().page().media() == null) {
                throw limiter.pause("anilist_unavailable", 30);
            }
            List<AniListResponse.Media> media = response.data().page().media();
            if (media.stream().anyMatch(item -> item == null || item.id() == null
                    || item.title() == null
                    || (item.title().romaji() == null && item.title().english() == null))) {
                throw limiter.pause("anilist_unavailable", 30);
            }
            return List.copyOf(media);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (variables.containsKey("id") && e.getStatusCode().value() == 404) {
                throw ApiException.notFound("anilist_media_not_found");
            }
            boolean limited = e.getStatusCode().value() == 429;
            // Do not echo external bodies or user search terms into logs.
            log.warn("AniList HTTP failure: {}", e.getStatusCode().value());
            throw limiter.pause(limited ? "anilist_rate_limited" : "anilist_unavailable",
                    retryAfter(e.getResponseHeaders(), limited ? 60 : 30));
        } catch (Exception e) {
            log.warn("AniList request failed: {}", e.getClass().getSimpleName());
            throw limiter.pause("anilist_unavailable", 30);
        }
    }

    static int retryAfter(HttpHeaders headers, int fallback) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return fallback;
        try {
            long seconds = value.trim().matches("[0-9]+")
                    ? Long.parseLong(value.trim())
                    : Duration.between(Instant.now(),
                            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant()).getSeconds();
            // Bound untrusted upstream input to one day.
            return (int) Math.max(1, Math.min(86_400, seconds));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
