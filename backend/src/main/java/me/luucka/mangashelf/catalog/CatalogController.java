package me.luucka.mangashelf.catalog;

import jakarta.validation.Valid;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.catalog.dto.MangaRequest;
import me.luucka.mangashelf.catalog.dto.MangaResponse;
import me.luucka.mangashelf.catalog.dto.SeriesRequest;
import me.luucka.mangashelf.catalog.dto.SeriesResponse;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST surface for the shared catalogue.
 *
 * <p>Routes are nested to mirror the ownership chain — editions are reached
 * through their work — so a URL always states what an edition belongs to.
 * Updates and deletes address a resource directly by id, since at that point
 * the parent is already fixed.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    // ---------------------------------------------------------------- manga

    @GetMapping("/manga")
    public Page<MangaResponse> listManga(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 24, sort = "titleRomaji", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return catalog.listManga(q, pageable).map(MangaResponse::from);
    }

    @GetMapping("/manga/{id}")
    public MangaResponse getManga(@PathVariable Long id) {
        return MangaResponse.from(catalog.getManga(id));
    }

    @PostMapping("/manga")
    public ResponseEntity<MangaResponse> createManga(@Valid @RequestBody MangaRequest request) {
        Manga manga = catalog.createManga(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MangaResponse.from(manga));
    }

    @PutMapping("/manga/{id}")
    public MangaResponse updateManga(@PathVariable Long id,
                                     @Valid @RequestBody MangaRequest request) {
        return MangaResponse.from(catalog.updateManga(id, request));
    }

    /**
     * Replaces a work's cover with an uploaded image.
     *
     * <p>Separate from the update endpoint because a file cannot travel in a
     * JSON body, and because the two are used at different moments: the form
     * is saved often, the cover is chosen once.
     */
    @PostMapping("/manga/{id}/cover")
    public MangaResponse uploadCover(@PathVariable Long id,
                                     @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("empty_file");
        }
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/")) {
            throw ApiException.badRequest("not_an_image");
        }
        try {
            return MangaResponse.from(
                    catalog.setCover(id, file.getBytes()));
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("unreadable_file");
        }
    }

    @DeleteMapping("/manga/{id}")
    public ResponseEntity<Void> deleteManga(@PathVariable Long id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        catalog.deleteManga(id, principal);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- series

    @GetMapping("/manga/{mangaId}/series")
    public List<SeriesResponse> listSeries(@PathVariable Long mangaId) {
        return catalog.listSeries(mangaId).stream().map(SeriesResponse::from).toList();
    }

    @GetMapping("/series/{id}")
    public SeriesResponse getSeries(@PathVariable Long id) {
        return SeriesResponse.from(catalog.getSeries(id));
    }

    @PostMapping("/manga/{mangaId}/series")
    public ResponseEntity<SeriesResponse> createSeries(
            @PathVariable Long mangaId,
            @Valid @RequestBody SeriesRequest request) {
        Series series = catalog.createSeries(mangaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SeriesResponse.from(series));
    }

    @PutMapping("/series/{id}")
    public SeriesResponse updateSeries(@PathVariable Long id,
                                       @Valid @RequestBody SeriesRequest request) {
        return SeriesResponse.from(catalog.updateSeries(id, request));
    }

    @DeleteMapping("/series/{id}")
    public ResponseEntity<Void> deleteSeries(@PathVariable Long id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        catalog.deleteSeries(id, principal);
        return ResponseEntity.noContent().build();
    }

}
