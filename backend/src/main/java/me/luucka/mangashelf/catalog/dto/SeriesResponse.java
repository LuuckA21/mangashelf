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
        boolean completed,
        int volumeCount
) {

    /**
     * Reads {@code volumes.size()}, so callers must pass a series whose
     * collection is already loaded — otherwise this triggers a lazy load per
     * series and turns a list endpoint into an N+1 query.
     */
    public static SeriesResponse from(Series s) {
        return new SeriesResponse(
                s.getId(), s.getManga().getId(), s.getManga().displayTitle(),
                s.getPublisher(), s.getLanguage(), s.getName(),
                s.getTotalVolumes(), s.isCompleted(), s.getVolumes().size());
    }
}
