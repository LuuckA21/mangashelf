package me.luucka.mangashelf.collection.dto;

import me.luucka.mangashelf.collection.UserVolume;

import java.time.Instant;

/** An owned volume, with enough context to display it in a list. */
public record UserVolumeResponse(
        Long seriesId,
        Short number,
        String seriesName,
        String publisher,
        Long mangaId,
        String mangaTitle,
        Instant addedAt
) {

    public static UserVolumeResponse from(UserVolume uv) {
        var series = uv.getSeries();
        return new UserVolumeResponse(
                series.getId(),
                uv.getNumber(),
                series.getName(),
                series.getPublisher(),
                series.getManga().getId(),
                series.getManga().displayTitle(),
                uv.getAddedAt());
    }
}
