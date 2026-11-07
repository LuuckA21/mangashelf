package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null = unchanged; discountAuto=true clears the fixed discount. */
public record ListPatch(
    @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "non deve essere vuoto")
        String description,
    @Min(1) @Max(12) Integer month,
    @Min(2000) @Max(2100) Integer year,
    @Min(0) @Max(100_000_000) Long discountCents,
    Boolean discountAuto,
    @Min(0) @Max(100_000_000) Long pointsDiscountCents) {}
