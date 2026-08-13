package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.luucka.mangashelf.catalog.PublicationStatus;

/** Payload for creating or updating a work by hand. */
public record MangaRequest(

        @NotBlank @Size(max = 500)
        String titleRomaji,

        @Size(max = 500) String titleNative,
        @Size(max = 500) String titleEnglish,
        String authors,
        String description,
        @Size(max = 1000) String coverUrl,
        PublicationStatus status,
        String[] genres,
        Short startYear,
        Short totalVolumes
) {
}
