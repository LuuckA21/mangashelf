package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeriesRequest(
    @NotBlank(message = "Campo obbligatorio") @Size(max = 200) String title) {}
