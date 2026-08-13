package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VolumeRequest(

        @NotNull @Min(0) @Max(999)
        Short number,

        @Size(max = 300) String title,

        @Pattern(regexp = "^$|^[0-9]{13}$", message = "must be 13 digits")
        String isbn13,

        LocalDate releaseDate,
        @Size(max = 1000) String coverUrl
) {
}
