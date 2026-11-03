package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeriesRequest(@NotBlank @Size(max = 200) String title) {}
