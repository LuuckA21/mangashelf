package me.luucka.mangashelf.collection.dto;

import me.luucka.mangashelf.collection.UserVolume;

import java.time.Instant;

/** An owned volume, with just enough context to display it in a list. */
public record UserVolumeResponse(
        Long volumeId,
        Short number,
        String volumeTitle,
        Long seriesId,
        String seriesName,
        String publisher,
        Long mangaId,
        String mangaTitle,
        Instant addedAt
) {

    /**
     * Walks volume to series to manga, so the caller must supply an entity
     * whose associations are loaded — otherwise this triggers extra queries
     * per row.
     */
    public static UserVolumeResponse from(UserVolume uv) {
        var volume = uv.getVolume();
        var series = volume.getSeries();
        var manga = series.getManga();
        return new UserVolumeResponse(
                volume.getId(), volume.getNumber(), volume.getTitle(),
                series.getId(), series.getName(), series.getPublisher(),
                manga.getId(), manga.displayTitle(), uv.getAddedAt());
    }
}
