package me.luucka.mangashelf.catalog;

import me.luucka.mangashelf.catalog.dto.MangaRequest;
import me.luucka.mangashelf.catalog.dto.SeriesRequest;
import me.luucka.mangashelf.collection.UserVolumeRepository;
import me.luucka.mangashelf.purchase.PurchaseItemRepository;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.common.CoverStore;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Write and read operations on the shared catalogue.
 *
 * <p>Unlike the collection tables, everything here is visible to every user:
 * one person adding <em>Berserk</em> means nobody else has to. That sharing
 * is what makes deletion the delicate operation — see
 * {@link #deleteManga(Long, UserPrincipal)}.
 */
@Service
public class CatalogService {

    private final MangaRepository mangaRepository;
    private final SeriesRepository seriesRepository;
    private final UserVolumeRepository userVolumeRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final CoverStore covers;

    public CatalogService(MangaRepository mangaRepository,
                          SeriesRepository seriesRepository,
                          UserVolumeRepository userVolumeRepository,
                          PurchaseItemRepository purchaseItemRepository,
                          CoverStore covers) {
        this.mangaRepository = mangaRepository;
        this.seriesRepository = seriesRepository;
        this.userVolumeRepository = userVolumeRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.covers = covers;
    }

    // ---------------------------------------------------------------- manga

    @Transactional(readOnly = true)
    public Page<Manga> listManga(String query, Pageable pageable) {
        return (query == null || query.isBlank())
                ? mangaRepository.findAll(pageable)
                : mangaRepository.search(query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Manga getManga(Long id) {
        return mangaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("manga_not_found"));
    }

    @Transactional
    public Manga createManga(MangaRequest request) {
        Manga manga = new Manga(request.titleRomaji());
        apply(manga, request);
        // Saved first because the file name is derived from the id, which
        // only exists once the row does.
        Manga saved = mangaRepository.save(manga);
        localiseCover(saved);
        return saved;
    }

    @Transactional
    public Manga updateManga(Long id, MangaRequest request) {
        Manga manga = getManga(id);
        manga.setTitleRomaji(request.titleRomaji());
        apply(manga, request);
        localiseCover(manga);
        return manga;
    }

    /**
     * Pulls a pasted cover URL onto local disk.
     *
     * <p>A remote address in the form field would otherwise stay a hotlink:
     * the image would break the day that host changes, and every visitor's
     * address would be handed to it. Values already served from
     * {@code /covers/} are left alone, so re-saving a form costs nothing.
     */
    private void localiseCover(Manga manga) {
        String url = manga.getCoverUrl();
        if (covers.isRemote(url)) {
            manga.setCoverUrl(covers.store(url, "manga-" + manga.getId()));
        }
    }

    /** Replaces the cover with an uploaded file. */
    @Transactional
    public Manga setCover(Long id, byte[] bytes) {
        Manga manga = getManga(id);
        manga.setCoverUrl(covers.storeBytes(bytes, "manga-" + id));
        return manga;
    }

    private void apply(Manga manga, MangaRequest request) {
        manga.setTitleNative(request.titleNative());
        manga.setTitleEnglish(request.titleEnglish());
        manga.setAuthors(request.authors());
        manga.setDescription(request.description());
        manga.setCoverUrl(request.coverUrl());
        manga.setStatus(request.status());
        manga.setGenres(request.genres());
        manga.setStartYear(request.startYear());
        manga.setTotalVolumes(request.totalVolumes());
    }

    /**
     * Removes a work and everything under it.
     *
     * <p>The cascade reaches other people's shelves: deleting a manga drops
     * its series, their volumes, and every {@code user_volume} row pointing
     * at them. Because the catalogue is shared, one user could otherwise
     * erase another user's collection with a single call. So deletion is
     * refused whenever any copy is owned, and only an administrator may
     * delete at all.
     */
    @Transactional
    public void deleteManga(Long id, UserPrincipal principal) {
        requireAdmin(principal);
        Manga manga = getManga(id);
        // One query for the whole work rather than one per edition.
        if (userVolumeRepository.countByMangaId(id) > 0) {
            throw ApiException.conflict("manga_has_owned_volumes");
        }
        // Purchase lines cascade from the edition too, so deleting a work
        // would strip rows from lists that may not even be the caller's.
        if (purchaseItemRepository.countByMangaId(id) > 0) {
            throw ApiException.conflict("manga_in_purchase_list");
        }
        mangaRepository.delete(manga);
    }

    // --------------------------------------------------------------- series

    @Transactional(readOnly = true)
    public List<Series> listSeries(Long mangaId) {
        return seriesRepository.findByMangaId(mangaId);
    }

    @Transactional(readOnly = true)
    public Series getSeries(Long id) {
        return seriesRepository.findWithMangaById(id)
                .orElseThrow(() -> ApiException.notFound("series_not_found"));
    }

    @Transactional
    public Series createSeries(Long mangaId, SeriesRequest request) {
        Manga manga = getManga(mangaId);
        String language = request.language() == null ? "it" : request.language();

        seriesRepository
                .findByMangaIdAndPublisherIgnoreCaseAndLanguageAndNameIgnoreCase(
                        mangaId, request.publisher(), language, request.name())
                .ifPresent(existing -> {
                    throw ApiException.conflict("series_already_exists");
                });

        Series series = new Series(manga, request.publisher(), request.name());
        series.setLanguage(language);
        series.setTotalVolumes(request.totalVolumes());
        series.setCompleted(request.completed());
        return seriesRepository.save(series);
    }

    @Transactional
    public Series updateSeries(Long id, SeriesRequest request) {
        Series series = getSeries(id);
        String language = request.language() == null ? series.getLanguage() : request.language();

        // The same publisher/language/name triple under one work violates the
        // unique constraint. Catching it here returns a readable message
        // rather than letting the database error surface as a 500.
        seriesRepository
                .findByMangaIdAndPublisherIgnoreCaseAndLanguageAndNameIgnoreCase(
                        series.getManga().getId(), request.publisher(), language, request.name())
                .ifPresent(other -> {
                    if (!other.getId().equals(id)) {
                        throw ApiException.conflict("series_already_exists");
                    }
                });

        series.setPublisher(request.publisher());
        series.setName(request.name());
        series.setLanguage(language);
        series.setTotalVolumes(request.totalVolumes());
        series.setCompleted(request.completed());
        return series;
    }

    @Transactional
    public void deleteSeries(Long id, UserPrincipal principal) {
        requireAdmin(principal);
        Series series = getSeries(id);
        if (userVolumeRepository.countByIdSeriesId(id) > 0) {
            throw ApiException.conflict("series_has_owned_volumes");
        }
        if (purchaseItemRepository.countBySeriesId(id) > 0) {
            throw ApiException.conflict("series_in_purchase_list");
        }
        seriesRepository.delete(series);
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw ApiException.forbidden("admin_required");
        }
    }
}
