package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** releaseDate null = each row keeps its own date. */
public record MoveRequest(
    @NotEmpty List<@NotNull UUID> itemIds, @NotNull UUID targetListId, LocalDate releaseDate) {}
