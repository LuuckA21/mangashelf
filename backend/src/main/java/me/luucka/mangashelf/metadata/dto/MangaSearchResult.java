package me.luucka.mangashelf.metadata.dto;

/**
 * One candidate from an external search, shown to the user before import.
 *
 * @param alreadyInCatalogue true when this work has already been imported,
 *                           so the interface can offer to open it instead of
 *                           creating a duplicate
 */
public record MangaSearchResult(
        Integer anilistId,
        String titleRomaji,
        String titleEnglish,
        String titleNative,
        String authors,
        String coverUrl,
        String status,
        Integer startYear,
        Integer totalVolumes,
        boolean alreadyInCatalogue,
        Long mangaId
) {
}
