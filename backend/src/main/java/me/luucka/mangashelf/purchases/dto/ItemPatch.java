package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;

/** Null = unchanged; clearReleaseDate=true sets the date to null. */
public record ItemPatch(
    UUID editionId,
    @Min(1) @Max(9999) Integer volume,
    LocalDate releaseDate,
    Boolean clearReleaseDate,
    @Min(0) @Max(100_000_000) Long priceEurCents,
    @Min(0) @Max(100_000_000) Long priceChfCents,
    Boolean booked) {}
