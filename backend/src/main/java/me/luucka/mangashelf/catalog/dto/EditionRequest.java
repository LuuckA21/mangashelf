package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;

/** {@code version} is required on PUT only (checked by the controller). */
public record EditionRequest(
    @NotBlank(message = "Campo obbligatorio") @Size(max = 200) String name,
    UUID publisherId,
    @NotNull EditionType type,
    @NotNull EditionStatus status,
    @Min(1) @Max(9999) int volumeCount,
    Long version) {}
