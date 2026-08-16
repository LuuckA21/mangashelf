package me.luucka.mangashelf.catalog.dto;

import me.luucka.mangashelf.catalog.Series;

public record SeriesResponse(
        Long id,
        Long mangaId,
        String mangaTitle,
        String publisher,
        String language,
        String name,
        Short totalVolumes,
        boolean completed
) {

    /**
     * Touches the work, so callers must pass a series whose manga is loaded —
     * otherwise this fires a query per row of a list endpoint.
     */
    public static SeriesResponse from(Series s) {
        return new SeriesResponse(
                s.getId(), s.getManga().getId(), s.getManga().displayTitle(),
                s.getPublisher(), s.getLanguage(), s.getName(),
                s.getTotalVolumes(), s.isCompleted());
    }
}
