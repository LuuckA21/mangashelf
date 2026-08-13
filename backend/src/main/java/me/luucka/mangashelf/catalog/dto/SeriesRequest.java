package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating a published run. */
public record SeriesRequest(

        @NotBlank @Size(max = 200)
        String publisher,

        @NotBlank @Size(max = 300)
        String name,

        @Pattern(regexp = "^[a-z]{2}$", message = "must be a two-letter ISO 639-1 code")
        String language,

        Short totalVolumes,
        boolean completed
) {
}
