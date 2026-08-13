package me.luucka.mangashelf.catalog.dto;

import me.luucka.mangashelf.catalog.Manga;
import me.luucka.mangashelf.catalog.PublicationStatus;

/** Summary view used in lists and search results. */
public record MangaResponse(
        Long id,
        String titleRomaji,
        String titleNative,
        String titleEnglish,
        String displayTitle,
        String authors,
        String description,
        String coverUrl,
        PublicationStatus status,
        String[] genres,
        Short startYear,
        Short totalVolumes,
        Integer anilistId
) {

    public static MangaResponse from(Manga m) {
        return new MangaResponse(
                m.getId(), m.getTitleRomaji(), m.getTitleNative(), m.getTitleEnglish(),
                m.displayTitle(), m.getAuthors(), m.getDescription(),
                m.getCoverUrl(), m.getStatus(),
                m.getGenres(), m.getStartYear(), m.getTotalVolumes(), m.getAnilistId());
    }
}
