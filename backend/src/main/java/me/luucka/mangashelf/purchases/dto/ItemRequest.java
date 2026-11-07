package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record ItemRequest(
    @NotNull UUID editionId,
    @NotNull @Min(1) @Max(9999) Integer volume,
    LocalDate releaseDate,
    @NotNull @Min(0) @Max(100_000_000) Long priceEurCents,
    @NotNull @Min(0) @Max(100_000_000) Long priceChfCents,
    Boolean booked) {}
