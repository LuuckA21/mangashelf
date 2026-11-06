package me.luucka.mangashelf.publishers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublisherRequest(
    @NotBlank(message = "Campo obbligatorio") @Size(max = 100) String name) {}
