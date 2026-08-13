package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.catalog.Manga;
import me.luucka.mangashelf.catalog.dto.MangaResponse;
import me.luucka.mangashelf.metadata.dto.MangaSearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Import of work metadata from AniList.
 *
 * <p>Lives under {@code /api/metadata}, which {@code SecurityConfig}
 * restricts to administrators: importing writes to the shared catalogue, so
 * it belongs with the other catalogue writes rather than with the personal
 * collection endpoints.
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final MetadataService metadata;

    public MetadataController(MetadataService metadata) {
        this.metadata = metadata;
    }

    @GetMapping("/search")
    public List<MangaSearchResult> search(@RequestParam String q,
                                          @RequestParam(defaultValue = "10") int limit) {
        return metadata.search(q, Math.min(limit, 25));
    }

    @PostMapping("/import/{anilistId}")
    public ResponseEntity<MangaResponse> importManga(@PathVariable int anilistId) {
        Manga manga = metadata.importByAnilistId(anilistId);
        return ResponseEntity.status(HttpStatus.CREATED).body(MangaResponse.from(manga));
    }
}
