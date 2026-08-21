package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import me.luucka.mangashelf.catalog.PublicationStatus;

/** Payload for creating or updating a work by hand. */
public record MangaRequest(

        @NotBlank @Size(max = 500)
        String titleRomaji,

        @Size(max = 500) String titleNative,
        @Size(max = 500) String titleEnglish,
        @Size(max = 500) String authors,
        // Generous but bounded: a synopsis is long, an accidental paste of a
        // whole page is not, and an unbounded text column invites the latter.
        @Size(max = 20000) String description,
        @Size(max = 1000) String coverUrl,
        PublicationStatus status,
        @Size(max = 50) String[] genres,
        @Min(1800) @Max(2200) Short startYear,
        @Min(0) @Max(999) Short totalVolumes
) {
}
