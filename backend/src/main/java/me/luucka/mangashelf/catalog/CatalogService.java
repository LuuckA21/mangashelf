package me.luucka.mangashelf.catalog;

import me.luucka.mangashelf.catalog.dto.BulkVolumeRequest;
import me.luucka.mangashelf.catalog.dto.MangaRequest;
import me.luucka.mangashelf.catalog.dto.SeriesRequest;
import me.luucka.mangashelf.catalog.dto.VolumeRequest;
import me.luucka.mangashelf.collection.UserVolumeRepository;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.Role;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final VolumeRepository volumeRepository;
    private final UserVolumeRepository userVolumeRepository;

    public CatalogService(MangaRepository mangaRepository,
                          SeriesRepository seriesRepository,
                          VolumeRepository volumeRepository,
                          UserVolumeRepository userVolumeRepository) {
        this.mangaRepository = mangaRepository;
        this.seriesRepository = seriesRepository;
        this.volumeRepository = volumeRepository;
        this.userVolumeRepository = userVolumeRepository;
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
        return mangaRepository.save(manga);
    }

    @Transactional
    public Manga updateManga(Long id, MangaRequest request) {
        Manga manga = getManga(id);
        manga.setTitleRomaji(request.titleRomaji());
        apply(manga, request);
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
        for (Series series : manga.getSeries()) {
            if (userVolumeRepository.countOwnedInSeries(series.getId()) > 0) {
                throw ApiException.conflict("manga_has_owned_volumes");
            }
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
        return seriesRepository.findWithVolumesById(id)
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
        series.setPublisher(request.publisher());
        series.setName(request.name());
        if (request.language() != null) series.setLanguage(request.language());
        series.setTotalVolumes(request.totalVolumes());
        series.setCompleted(request.completed());
        return series;
    }

    @Transactional
    public void deleteSeries(Long id, UserPrincipal principal) {
        requireAdmin(principal);
        Series series = getSeries(id);
        if (userVolumeRepository.countOwnedInSeries(id) > 0) {
            throw ApiException.conflict("series_has_owned_volumes");
        }
        seriesRepository.delete(series);
    }

    // --------------------------------------------------------------- volume

    @Transactional(readOnly = true)
    public List<Volume> listVolumes(Long seriesId) {
        return volumeRepository.findBySeriesIdOrderByNumberAsc(seriesId);
    }

    @Transactional(readOnly = true)
    public Volume getVolume(Long id) {
        return volumeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("volume_not_found"));
    }

    @Transactional
    public Volume createVolume(Long seriesId, VolumeRequest request) {
        Series series = getSeries(seriesId);
        volumeRepository.findBySeriesIdAndNumber(seriesId, request.number())
                .ifPresent(existing -> {
                    throw ApiException.conflict("volume_already_exists");
                });

        Volume volume = new Volume(series, request.number());
        apply(volume, request);
        return volumeRepository.save(volume);
    }

    /**
     * Creates every missing number in the requested range.
     *
     * <p>Existing numbers are skipped instead of failing the whole call, so
     * re-running the range after a run grows adds only the new tomes.
     */
    @Transactional
    public List<Volume> createVolumes(Long seriesId, BulkVolumeRequest request) {
        if (request.to() < request.from()) {
            throw ApiException.badRequest("invalid_range");
        }

        Series series = getSeries(seriesId);
        Set<Short> existing = new HashSet<>();
        for (Volume v : series.getVolumes()) {
            existing.add(v.getNumber());
        }

        List<Volume> created = new ArrayList<>();
        for (short n = request.from(); n <= request.to(); n++) {
            if (existing.contains(n)) continue;
            created.add(volumeRepository.save(new Volume(series, n)));
        }
        return created;
    }

    @Transactional
    public Volume updateVolume(Long id, VolumeRequest request) {
        Volume volume = getVolume(id);
        Short newNumber = request.number();
        if (!newNumber.equals(volume.getNumber())) {
            volumeRepository
                    .findBySeriesIdAndNumber(volume.getSeries().getId(), newNumber)
                    .ifPresent(other -> {
                        throw ApiException.conflict("volume_already_exists");
                    });
            volume.setNumber(newNumber);
        }
        apply(volume, request);
        return volume;
    }

    private void apply(Volume volume, VolumeRequest request) {
        volume.setTitle(request.title());
        // An empty string would violate nothing but is not a valid ISBN
        // either, so it is normalised away to null.
        volume.setIsbn13(request.isbn13() == null || request.isbn13().isBlank()
                ? null : request.isbn13());
        volume.setReleaseDate(request.releaseDate());
        volume.setCoverUrl(request.coverUrl());
    }

    @Transactional
    public void deleteVolume(Long id, UserPrincipal principal) {
        requireAdmin(principal);
        Volume volume = getVolume(id);
        if (userVolumeRepository.countByIdVolumeId(id) > 0) {
            throw ApiException.conflict("volume_is_owned");
        }
        volumeRepository.delete(volume);
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw ApiException.forbidden("admin_required");
        }
    }
}
