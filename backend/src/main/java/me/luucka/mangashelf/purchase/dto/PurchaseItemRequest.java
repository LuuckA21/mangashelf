package me.luucka.mangashelf.purchase.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PurchaseItemRequest(
        @NotNull Long seriesId,
        @NotNull @Min(0) @Max(999) Short volumeNumber,
        LocalDate releaseDate,
        @Min(0) Integer priceEurCents,
        @Min(0) Integer priceChfCents
) {
}
