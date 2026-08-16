package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.catalog.Manga;
import me.luucka.mangashelf.catalog.MangaRepository;
import me.luucka.mangashelf.catalog.PublicationStatus;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.common.CoverStore;
import me.luucka.mangashelf.metadata.dto.AniListResponse;
import me.luucka.mangashelf.metadata.dto.MangaSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Turns AniList results into catalogue rows. */
@Service
public class MetadataService {

    private final AniListClient anilist;
    private final MangaRepository mangaRepository;
    private final CoverStore covers;

    public MetadataService(AniListClient anilist,
                           MangaRepository mangaRepository,
                           CoverStore covers) {
        this.anilist = anilist;
        this.mangaRepository = mangaRepository;
        this.covers = covers;
    }

    /**
     * Searches AniList and marks which results are already catalogued, so
     * the interface can steer to the existing entry instead of letting the
     * user create a duplicate.
     */
    @Transactional(readOnly = true)
    public List<MangaSearchResult> search(String term, int limit) {
        return anilist.search(term, limit).stream().map(media -> {
            Optional<Manga> existing = mangaRepository.findByAnilistId(media.id());
            return new MangaSearchResult(
                    media.id(),
                    titleRomaji(media),
                    media.title() == null ? null : media.title().english(),
                    media.title() == null ? null : media.title().nativeTitle(),
                    authors(media),
                    coverUrl(media),
                    media.status(),
                    media.startDate() == null ? null : media.startDate().year(),
                    media.volumes(),
                    existing.isPresent(),
                    existing.map(Manga::getId).orElse(null));
        }).toList();
    }

    /**
     * Imports a work, or refreshes it if already present.
     *
     * <p>Matching on the AniList id rather than the title is what keeps a
     * second import from creating a duplicate: titles vary by romanisation,
     * ids do not.
     */
    @Transactional
    public Manga importByAnilistId(int anilistId) {
        AniListResponse.Media media = anilist.byId(anilistId);

        Manga manga = mangaRepository.findByAnilistId(anilistId)
                .orElseGet(() -> new Manga(titleRomaji(media)));

        manga.setAnilistId(media.id());
        // The MAL id is unique in the schema, so a null must stay null
        // rather than becoming a zero that a second import would collide on.
        manga.setMalId(media.idMal());
        manga.setTitleRomaji(titleRomaji(media));
        if (media.title() != null) {
            manga.setTitleEnglish(media.title().english());
            manga.setTitleNative(media.title().nativeTitle());
        }
        manga.setAuthors(authors(media));
        manga.setDescription(plainText(media.description()));
        // Downloaded at import time rather than on first view: the import is
        // already a slow, explicit action, while a page load is not.
        String cover = coverUrl(media);
        if (cover != null) {
            manga.setCoverUrl(covers.store(cover, "anilist-" + media.id()));
        }
        manga.setStatus(status(media.status()));
        if (media.genres() != null) {
            manga.setGenres(media.genres().toArray(String[]::new));
        }
        if (media.startDate() != null && media.startDate().year() != null) {
            manga.setStartYear(media.startDate().year().shortValue());
        }
        if (media.volumes() != null) {
            manga.setTotalVolumes(media.volumes().shortValue());
        }
        manga.setSyncedAt(Instant.now());

        return mangaRepository.save(manga);
    }

    private String coverUrl(AniListResponse.Media media) {
        if (media.coverImage() == null) return null;
        return media.coverImage().extraLarge() != null
                ? media.coverImage().extraLarge()
                : media.coverImage().large();
    }

    private String titleRomaji(AniListResponse.Media media) {
        if (media.title() != null && media.title().romaji() != null) {
            return media.title().romaji();
        }
        if (media.title() != null && media.title().english() != null) {
            return media.title().english();
        }
        throw ApiException.badRequest("anilist_media_without_title");
    }

    /**
     * Keeps only the people who wrote or drew the work. AniList lists
     * translators, letterers and assistants under the same staff edge, and
     * a catalogue line naming five roles helps nobody.
     */
    private String authors(AniListResponse.Media media) {
        if (media.staff() == null || media.staff().edges() == null) return null;

        List<String> names = media.staff().edges().stream()
                .filter(edge -> edge.role() == null
                        || edge.role().toLowerCase().contains("story")
                        || edge.role().toLowerCase().contains("art"))
                .map(edge -> edge.node() == null || edge.node().name() == null
                        ? null : edge.node().name().full())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(3)
                .toList();

        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** AniList descriptions carry HTML even when asked for plain text. */
    private String plainText(String description) {
        if (description == null) return null;
        return description
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .trim();
    }

    /**
     * AniList uses the same vocabulary as {@link PublicationStatus}, but an
     * unknown value must not stop an import: better a work with no status
     * than no work at all.
     */
    private PublicationStatus status(String value) {
        if (value == null) return null;
        try {
            return PublicationStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
