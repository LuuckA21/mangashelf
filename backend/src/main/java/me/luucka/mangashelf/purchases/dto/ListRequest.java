package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListRequest(
    @NotBlank @Size(max = 100) String description,
    @NotNull @Min(1) @Max(12) Integer month,
    @NotNull @Min(2000) @Max(2100) Integer year) {}
