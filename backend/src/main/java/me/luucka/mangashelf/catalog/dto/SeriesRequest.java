package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

        // Bounded because the shelf is drawn up to this number: a careless
        // 30000 would make every page of that edition build a list that long.
        @Min(0) @Max(999) Short totalVolumes,
        boolean completed
) {
}
