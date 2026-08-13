package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.metadata.dto.AniListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Reads work metadata from AniList's public GraphQL API.
 *
 * <p>No API key is involved: the endpoint is open for reads. The service
 * allows 90 requests a minute and enforces a separate burst limiter, so all
 * traffic goes through {@link RateLimiter} rather than being fired off as
 * fast as the caller asks.
 */
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

    /**
     * Builds the client with {@code RestClient.create} rather than injecting
     * a {@code RestClient.Builder}.
     *
     * <p>In Spring Boot 4 the builder's autoconfiguration lives in a
     * separate module, so injecting it fails at startup unless that module
     * is on the classpath. The static factory is part of {@code spring-web}
     * itself and needs nothing else — and this client wants no shared
     * interceptors anyway.
     */
    public AniListClient(@Value("${app.metadata.anilist-url}") String url,
                         @Value("${app.metadata.anilist-requests-per-minute}") int perMinute) {
        this.http = RestClient.create(url);
        this.limiter = new RateLimiter(perMinute);
    }

    public List<AniListResponse.Media> search(String term, int limit) {
        return execute(SEARCH_QUERY, Map.of("search", term, "perPage", limit));
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
            AniListResponse response = http.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .body(AniListResponse.class);

            if (response == null || response.data() == null || response.data().page() == null) {
                return List.of();
            }
            List<AniListResponse.Media> media = response.data().page().media();
            return media == null ? List.of() : media;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // The catalogue must stay usable when an external service is
            // down, so the failure is reported as such rather than as a
            // generic 500 that says nothing about whose fault it was.
            log.warn("Chiamata ad AniList fallita: {}", e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "anilist_unavailable");
        }
    }
}
