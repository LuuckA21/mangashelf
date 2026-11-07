package me.luucka.mangashelf.purchases.dto;

import java.time.LocalDate;

/** Values proposed by the add-row form; null = nothing to propose. */
public record Proposal(Integer volume, Long priceEurCents, LocalDate releaseDate) {}
