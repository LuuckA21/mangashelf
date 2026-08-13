package me.luucka.mangashelf.catalog.dto;

import me.luucka.mangashelf.catalog.Volume;

import java.time.LocalDate;

public record VolumeResponse(
        Long id,
        Long seriesId,
        Short number,
        String title,
        String isbn13,
        LocalDate releaseDate,
        String coverUrl,
        boolean upcoming
) {

    public static VolumeResponse from(Volume v) {
        return new VolumeResponse(
                v.getId(), v.getSeries().getId(), v.getNumber(), v.getTitle(),
                v.getIsbn13(), v.getReleaseDate(),
                v.getCoverUrl(), v.isUpcoming());
    }
}
